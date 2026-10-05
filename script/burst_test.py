import asyncio
import os
import time
from collections import Counter

import aiohttp


BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
SHOW_ID = int(os.getenv("SHOW_ID", "3"))
SEAT = os.getenv("SEAT", "B1")
TOKEN = os.getenv("JWT_TOKEN")

TOTAL_REQUESTS = int(os.getenv("TOTAL_REQUESTS", "20000"))

# Maximum requests running at the same time.
CONCURRENCY = int(os.getenv("CONCURRENCY", "500"))


async def reserve_seat(session, semaphore, request_number):

    async with semaphore:

        url = f"{BASE_URL}/shows/{SHOW_ID}/reserve"

        headers = {
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
            "Idempotency-Key": f"burst-{request_number}",
            "X-Request-ID": f"burst-{request_number}"
        }

        body = {
            "seats": [SEAT]
        }

        start = time.perf_counter()

        try:

            async with session.post(
                url,
                headers=headers,
                json=body
            ) as response:

                elapsed = time.perf_counter() - start

                try:
                    response_body = await response.json()
                except Exception:
                    response_body = await response.text()

                return {
                    "request": request_number,
                    "status": response.status,
                    "elapsed": elapsed,
                    "body": response_body,
                    "error_type": None,
                    "error": None
                }

        except asyncio.TimeoutError as exc:

            elapsed = time.perf_counter() - start

            return {
                "request": request_number,
                "status": "ERROR",
                "elapsed": elapsed,
                "body": None,
                "error_type": "TIMEOUT",
                "error": repr(exc)
            }

        except aiohttp.ClientConnectionError as exc:

            elapsed = time.perf_counter() - start

            return {
                "request": request_number,
                "status": "ERROR",
                "elapsed": elapsed,
                "body": None,
                "error_type": "CONNECTION_ERROR",
                "error": repr(exc)
            }

        except aiohttp.ClientError as exc:

            elapsed = time.perf_counter() - start

            return {
                "request": request_number,
                "status": "ERROR",
                "elapsed": elapsed,
                "body": None,
                "error_type": "CLIENT_ERROR",
                "error": repr(exc)
            }

        except Exception as exc:

            elapsed = time.perf_counter() - start

            return {
                "request": request_number,
                "status": "ERROR",
                "elapsed": elapsed,
                "body": None,
                "error_type": "UNKNOWN_ERROR",
                "error": repr(exc)
            }


async def reconcile_show(session):

    url = f"{BASE_URL}/shows/{SHOW_ID}"

    try:

        async with session.get(url) as response:

            if response.status != 200:

                return {
                    "success": False,
                    "status": response.status,
                    "body": await response.text()
                }

            body = await response.json()

            return {
                "success": True,
                "status": response.status,
                "body": body
            }

    except Exception as exc:

        return {
            "success": False,
            "status": "ERROR",
            "body": repr(exc)
        }


async def main():

    if not TOKEN:

        raise RuntimeError(
            "JWT_TOKEN environment variable is required."
        )

    print("=" * 70)
    print("SEAT RESERVATION HOT-SEAT BURST TEST")
    print("=" * 70)

    print(f"Base URL       : {BASE_URL}")
    print(f"Show ID        : {SHOW_ID}")
    print(f"Hot Seat       : {SEAT}")
    print(f"Total Requests : {TOTAL_REQUESTS}")
    print(f"Concurrency    : {CONCURRENCY}")
    print()

    connector = aiohttp.TCPConnector(
        limit=CONCURRENCY,
        limit_per_host=CONCURRENCY,
        ttl_dns_cache=300
    )

    timeout = aiohttp.ClientTimeout(
        total=120,
        connect=60,
        sock_connect=60,
        sock_read=120
    )

    semaphore = asyncio.Semaphore(CONCURRENCY)

    async with aiohttp.ClientSession(
        connector=connector,
        timeout=timeout
    ) as session:

        start_time = time.perf_counter()

        tasks = [
            reserve_seat(
                session,
                semaphore,
                request_number
            )
            for request_number in range(
                1,
                TOTAL_REQUESTS + 1
            )
        ]

        results = await asyncio.gather(*tasks)

        total_time = time.perf_counter() - start_time

        reconciliation = await reconcile_show(session)

    # =========================================================
    # STATUS COUNTS
    # =========================================================

    status_counter = Counter(
        str(result["status"])
        for result in results
    )

    successful = status_counter.get("201", 0)

    conflicts = status_counter.get("409", 0)

    errors_5xx = sum(
        count
        for status, count in status_counter.items()
        if status.isdigit()
        and 500 <= int(status) <= 599
    )

    client_errors = sum(
        count
        for status, count in status_counter.items()
        if status.isdigit()
        and 400 <= int(status) <= 499
    )

    errors = status_counter.get("ERROR", 0)

    # =========================================================
    # ERROR TYPES
    # =========================================================

    error_type_counter = Counter(
        result["error_type"]
        for result in results
        if result["status"] == "ERROR"
    )

    # =========================================================
    # LATENCY
    # =========================================================

    durations = [
        result["elapsed"]
        for result in results
        if isinstance(result["status"], int)
    ]

    print()
    print("=" * 70)
    print("RESULT")
    print("=" * 70)

    print(f"Total requests     : {TOTAL_REQUESTS}")
    print(f"Concurrency        : {CONCURRENCY}")
    print(f"201 Created        : {successful}")
    print(f"409 Conflict       : {conflicts}")
    print(f"4xx total          : {client_errors}")
    print(f"5xx total          : {errors_5xx}")
    print(f"Client errors      : {errors}")
    print(f"Total test time    : {total_time:.3f} seconds")

    if total_time > 0:

        print(
            f"Requests/second    : "
            f"{TOTAL_REQUESTS / total_time:.2f}"
        )

    if durations:

        average_latency = (
            sum(durations)
            / len(durations)
            * 1000
        )

        max_latency = max(durations) * 1000

        print(
            f"Average latency    : "
            f"{average_latency:.2f} ms"
        )

        print(
            f"Max latency        : "
            f"{max_latency:.2f} ms"
        )

    # =========================================================
    # HTTP STATUS COUNTS
    # =========================================================

    print()
    print("HTTP STATUS COUNTS")
    print("-" * 30)

    for status, count in sorted(
        status_counter.items(),
        key=lambda item: str(item[0])
    ):

        print(f"{status}: {count}")

    # =========================================================
    # ERROR BREAKDOWN
    # =========================================================

    print()
    print("CLIENT ERROR BREAKDOWN")
    print("-" * 30)

    if error_type_counter:

        for error_type, count in error_type_counter.most_common():

            print(
                f"{error_type}: {count}"
            )

    else:

        print("No client/network errors.")

    # =========================================================
    # CONCURRENCY CHECK
    # =========================================================

    print()
    print("=" * 70)
    print("CONCURRENCY CHECK")
    print("=" * 70)

    expected_conflicts = TOTAL_REQUESTS - 1

    if successful == 1:

        print(
            "PASS: Exactly one request received 201."
        )

    else:

        print(
            f"FAIL: Expected exactly one 201, "
            f"but received {successful}."
        )

    if conflicts == expected_conflicts:

        print(
            "PASS: All remaining requests received 409."
        )

    else:

        print(
            f"INFO: Expected {expected_conflicts} conflicts, "
            f"received {conflicts}."
        )

    if errors_5xx == 0:

        print(
            "PASS: No 5xx responses."
        )

    else:

        print(
            f"FAIL: {errors_5xx} requests returned 5xx."
        )

    if errors == 0:

        print(
            "PASS: No client/network errors."
        )

    else:

        print(
            f"WARNING: {errors} client/network errors occurred."
        )

    # =========================================================
    # RECONCILIATION
    # =========================================================

    print()
    print("=" * 70)
    print("RECONCILIATION")
    print("=" * 70)

    if not reconciliation["success"]:

        print(
            "FAIL: Could not fetch show for reconciliation."
        )

        print(
            f"Response: {reconciliation['body']}"
        )

    else:

        show = reconciliation["body"]

        total_seats = show["total_seats"]
        available = show["available"]
        held = show["held"]
        confirmed = show["confirmed"]

        calculated_total = (
            available +
            held +
            confirmed
        )

        print(f"Total seats       : {total_seats}")
        print(f"Available         : {available}")
        print(f"Held              : {held}")
        print(f"Confirmed         : {confirmed}")

        print()

        print(
            f"Invariant check   : "
            f"{available} + {held} + {confirmed} = "
            f"{calculated_total}"
        )

        if calculated_total == total_seats:

            print(
                "PASS: Seat count invariant is valid."
            )

        else:

            print(
                "FAIL: Seat count invariant is broken."
            )

        hot_seat_status = None

        for seat in show["seats"]:

            if seat["seat"] == SEAT:

                hot_seat_status = seat["status"]
                break

        print(
            f"Hot seat {SEAT} status : "
            f"{hot_seat_status}"
        )

        if successful == 1 and hot_seat_status == "confirmed":

            print(
                f"PASS: Hot seat {SEAT} is confirmed exactly once."
            )

        else:

            print(
                f"FAIL: Hot seat {SEAT} reconciliation failed."
            )

    # =========================================================
    # FINAL SUMMARY
    # =========================================================

    print()
    print("=" * 70)
    print("FINAL SUMMARY")
    print("=" * 70)

    if (
        successful == 1
        and conflicts == TOTAL_REQUESTS - 1
        and errors_5xx == 0
        and errors == 0
    ):

        print(
            f"PASS: {TOTAL_REQUESTS} hot-seat concurrency "
            f"test fully passed."
        )

    else:

        print(
            f"PARTIAL PASS: {TOTAL_REQUESTS} requests were "
            f"generated, but not all completed successfully."
        )

    print("=" * 70)


if __name__ == "__main__":
    asyncio.run(main())