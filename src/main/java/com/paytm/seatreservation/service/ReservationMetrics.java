package com.paytm.seatreservation.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ReservationMetrics {

    private final MeterRegistry meterRegistry;

    private final Counter confirmedCounter;

    private final Map<String, Counter> declinedCounters = new ConcurrentHashMap<>();

    private final Map<Long, AtomicInteger> availableSeats = new ConcurrentHashMap<>();

    public ReservationMetrics(MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;

        this.confirmedCounter = Counter.builder("reservation_confirmed")
                .description("Number of confirmed reservations")
                .register(meterRegistry);
    }

    /**
     * Increment confirmed reservation counter.
     */
    public void reservationConfirmed() {
        confirmedCounter.increment();
    }

    /**
     * Increment declined reservation counter.
     *
     * Reasons:
     * - seat-taken
     * - per-user-limit
     * - idempotent-replay
     */
    public void reservationDeclined(String reason) {

        Counter counter = declinedCounters.computeIfAbsent(
                reason,
                key -> Counter.builder("reservation_declined")
                        .description("Number of declined reservation requests")
                        .tag("reason", key)
                        .register(meterRegistry));

        counter.increment();
    }

    /**
     * Register/update available seats gauge for a show.
     */
    public void setAvailableSeats(
            Long showId,
            int count) {

        AtomicInteger value = availableSeats.computeIfAbsent(
                showId,
                id -> {
                    AtomicInteger atomicInteger = new AtomicInteger();

                    Gauge.builder(
                            "seats_available",
                            atomicInteger,
                            AtomicInteger::get)
                            .description(
                                    "Number of currently available seats")
                            .tag("show_id", String.valueOf(id))
                            .register(meterRegistry);

                    return atomicInteger;
                });

        value.set(count);
    }
}