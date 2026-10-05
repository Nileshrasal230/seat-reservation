package com.paytm.seatreservation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paytm.seatreservation.entity.Reservation;

import jakarta.persistence.LockModeType;

public interface ReservationRepository
                extends JpaRepository<Reservation, UUID> {

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT r
                        FROM Reservation r
                        WHERE r.id = :reservationId
                        """)
        java.util.Optional<Reservation> findByIdForUpdate(
                        @Param("reservationId") UUID reservationId);
}