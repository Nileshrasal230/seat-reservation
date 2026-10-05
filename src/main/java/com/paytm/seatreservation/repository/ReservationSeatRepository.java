package com.paytm.seatreservation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paytm.seatreservation.entity.ReservationSeat;

public interface ReservationSeatRepository
                extends JpaRepository<ReservationSeat, Long> {

        @Query("""
                        SELECT COUNT(rs)
                        FROM ReservationSeat rs
                        JOIN rs.reservation r
                        WHERE r.user.id = :userId
                          AND r.show.id = :showId
                          AND r.status = 'CONFIRMED'
                        """)
        long countActiveSeatsForUserAndShow(
                        @Param("userId") Long userId,
                        @Param("showId") Long showId);

        @Query("""
                        SELECT rs.seat.seatNumber
                        FROM ReservationSeat rs
                        WHERE rs.reservation.id = :reservationId
                        ORDER BY rs.seat.seatNumber
                        """)
        List<String> findSeatNumbersByReservationId(
                        @Param("reservationId") UUID reservationId);

        List<ReservationSeat> findByReservation_Id(
                        UUID reservationId);
}