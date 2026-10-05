\# Design Write-Up — Seat Reservation at Scale



\## 1. Overview



This project implements a concurrent seat reservation backend using Java 17, Spring Boot, PostgreSQL and Docker.



The primary design goal is to maintain correctness under high concurrency, especially when many users attempt to reserve the same seat at the same time.



The implementation focuses on:



\- Preventing double booking

\- Atomic multi-seat reservations

\- Idempotent requests

\- Per-user reservation limits

\- Transactional consistency

\- Observability

\- Health and readiness checks

\- High-concurrency validation



\---



\## 2. Atomic Reservation Mechanism



The reservation operation is executed inside a database transaction.



The reservation flow is:



1\. Authenticate the user using JWT.

2\. Lock the user row.

3\. Check whether the idempotency key already exists.

4\. Sort the requested seats deterministically.

5\. Acquire pessimistic write locks on the requested seat rows.

6\. Validate that all requested seats are available.

7\. Check the user's confirmed seat count against the per-user limit.

8\. Create the reservation.

9\. Create reservation-seat mappings.

10\. Change the requested seats to `CONFIRMED`.

11\. Store the idempotency record.

12\. Commit the transaction.



If any validation fails, the transaction is rolled back and no partial reservation is created.



This provides all-or-nothing behaviour for multi-seat requests.



\---



\## 3. Why PostgreSQL Row-Level Locking



The main concurrency mechanism is PostgreSQL row-level locking through JPA.



The seat repository uses:



```java

@Lock(LockModeType.PESSIMISTIC\_WRITE)

