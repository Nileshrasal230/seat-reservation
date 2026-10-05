package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.dto.ReserveRequest;
import com.paytm.seatreservation.entity.IdempotencyKey;
import com.paytm.seatreservation.entity.Reservation;
import com.paytm.seatreservation.entity.ReservationSeat;
import com.paytm.seatreservation.entity.ReservationSeatId;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.entity.User;
import com.paytm.seatreservation.exception.ReservationConflictException;
import com.paytm.seatreservation.repository.IdempotencyKeyRepository;
import com.paytm.seatreservation.repository.ReservationRepository;
import com.paytm.seatreservation.repository.ReservationSeatRepository;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import com.paytm.seatreservation.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {

        private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

        private final ShowRepository showRepository;
        private final UserRepository userRepository;
        private final SeatRepository seatRepository;
        private final ReservationRepository reservationRepository;
        private final ReservationSeatRepository reservationSeatRepository;
        private final IdempotencyKeyRepository idempotencyKeyRepository;
        private final ReservationMetrics reservationMetrics;

        @Transactional
        public ReservationResponse reserve(
                        Long showId,
                        Long userId,
                        ReserveRequest request,
                        String idempotencyKey) {

                log.info(
                                "RESERVATION_STARTED showId={} userId={} idempotencyKey={} seats={}",
                                showId,
                                userId,
                                idempotencyKey,
                                request != null ? request.seats() : null);

                if (idempotencyKey == null || idempotencyKey.isBlank()) {

                        log.warn(
                                        "RESERVATION_REJECTED reason=missing-idempotency-key showId={} userId={}",
                                        showId,
                                        userId);

                        throw new ReservationConflictException(
                                        "Idempotency-Key header is required");
                }

                if (request == null ||
                                request.seats() == null ||
                                request.seats().isEmpty()) {

                        log.warn(
                                        "RESERVATION_REJECTED reason=no-seats showId={} userId={}",
                                        showId,
                                        userId);

                        throw new ReservationConflictException(
                                        "At least one seat is required");
                }

                User user = userRepository.findByIdForUpdate(userId)
                                .orElseThrow(() -> {

                                        log.warn(
                                                        "RESERVATION_REJECTED reason=user-not-found showId={} userId={}",
                                                        showId,
                                                        userId);

                                        return new ReservationConflictException(
                                                        "User not found");
                                });

                Show show = showRepository.findById(showId)
                                .orElseThrow(() -> {

                                        log.warn(
                                                        "RESERVATION_REJECTED reason=show-not-found showId={} userId={}",
                                                        showId,
                                                        userId);

                                        return new ReservationConflictException(
                                                        "Show not found");
                                });

                Optional<IdempotencyKey> existing = idempotencyKeyRepository
                                .findByUserIdAndShowIdAndIdempotencyKey(
                                                userId,
                                                showId,
                                                idempotencyKey);

                if (existing.isPresent()) {

                        IdempotencyKey existingKey = existing.get();

                        String currentHash = createRequestHash(request);

                        if (!existingKey.getRequestHash().equals(currentHash)) {

                                log.warn(
                                                "IDEMPOTENT_REPLAY_REJECTED reason=different-request showId={} userId={} idempotencyKey={}",
                                                showId,
                                                userId,
                                                idempotencyKey);

                                reservationMetrics.reservationDeclined(
                                                "idempotent-replay");

                                throw new ReservationConflictException(
                                                "Same Idempotency-Key used with different request");
                        }

                        Reservation original = reservationRepository
                                        .findById(existingKey.getReservationId())
                                        .orElseThrow(() -> {

                                                log.error(
                                                                "IDEMPOTENT_REPLAY_FAILED reservation-not-found reservationId={} showId={} userId={}",
                                                                existingKey.getReservationId(),
                                                                showId,
                                                                userId);

                                                return new ReservationConflictException(
                                                                "Original reservation not found");
                                        });

                        log.info(
                                        "IDEMPOTENT_REPLAY_SUCCESS reservationId={} showId={} userId={} idempotencyKey={}",
                                        original.getId(),
                                        showId,
                                        userId,
                                        idempotencyKey);

                        reservationMetrics.reservationDeclined(
                                        "idempotent-replay");

                        return toResponse(original);
                }

                List<String> requestedSeats = request.seats()
                                .stream()
                                .distinct()
                                .sorted()
                                .toList();

                if (requestedSeats.isEmpty()) {

                        log.warn(
                                        "RESERVATION_REJECTED reason=no-valid-seats showId={} userId={}",
                                        showId,
                                        userId);

                        throw new ReservationConflictException(
                                        "At least one valid seat is required");
                }

                List<Seat> seats = seatRepository.findSeatsForUpdate(
                                showId,
                                requestedSeats);

                if (seats.size() != requestedSeats.size()) {

                        log.warn(
                                        "RESERVATION_REJECTED reason=seat-not-found showId={} userId={} requestedSeats={}",
                                        showId,
                                        userId,
                                        requestedSeats);

                        throw new ReservationConflictException(
                                        "One or more requested seats do not exist");
                }

                for (Seat seat : seats) {

                        if (seat.getStatus() != Seat.SeatStatus.AVAILABLE) {

                                log.warn(
                                                "RESERVATION_REJECTED reason=seat-taken showId={} userId={} seat={}",
                                                showId,
                                                userId,
                                                seat.getSeatNumber());

                                reservationMetrics.reservationDeclined(
                                                "seat-taken");

                                throw new ReservationConflictException(
                                                "Seat already taken: " +
                                                                seat.getSeatNumber());
                        }
                }

                long existingSeatCount = reservationSeatRepository
                                .countActiveSeatsForUserAndShow(
                                                userId,
                                                showId);

                long requestedCount = requestedSeats.size();

                if (existingSeatCount + requestedCount > show.getPerUserLimit()) {

                        log.warn(
                                        "RESERVATION_REJECTED reason=per-user-limit showId={} userId={} existingSeats={} requestedSeats={} limit={}",
                                        showId,
                                        userId,
                                        existingSeatCount,
                                        requestedCount,
                                        show.getPerUserLimit());

                        reservationMetrics.reservationDeclined(
                                        "per-user-limit");

                        throw new ReservationConflictException(
                                        "Per-user seat limit exceeded");
                }

                long amount = show.getPricePaise() * requestedCount;

                Reservation reservation = Reservation.builder()
                                .id(UUID.randomUUID())
                                .show(show)
                                .user(user)
                                .amountPaise(amount)
                                .status(
                                                Reservation.ReservationStatus.CONFIRMED)
                                .build();

                Reservation savedReservation = reservationRepository.save(reservation);

                for (Seat seat : seats) {

                        seat.setStatus(
                                        Seat.SeatStatus.CONFIRMED);

                        ReservationSeatId reservationSeatId = new ReservationSeatId(
                                        savedReservation.getId(),
                                        seat.getId());

                        ReservationSeat reservationSeat = ReservationSeat.builder()
                                        .id(reservationSeatId)
                                        .reservation(savedReservation)
                                        .seat(seat)
                                        .build();

                        reservationSeatRepository.save(
                                        reservationSeat);
                }

                IdempotencyKey key = IdempotencyKey.builder()
                                .user(user)
                                .show(show)
                                .idempotencyKey(idempotencyKey)
                                .requestHash(
                                                createRequestHash(request))
                                .reservationId(
                                                savedReservation.getId())
                                .build();

                idempotencyKeyRepository.save(key);

                reservationMetrics.reservationConfirmed();

                updateAvailableSeats(
                                showId,
                                -requestedCount);

                log.info(
                                "RESERVATION_CONFIRMED reservationId={} showId={} userId={} seats={} amountPaise={}",
                                savedReservation.getId(),
                                showId,
                                userId,
                                requestedSeats,
                                amount);

                return toResponse(savedReservation);
        }

        @Transactional
        public ReservationResponse cancel(
                        UUID reservationId,
                        Long userId) {

                log.info(
                                "RESERVATION_CANCEL_STARTED reservationId={} userId={}",
                                reservationId,
                                userId);

                Reservation reservation = reservationRepository
                                .findByIdForUpdate(reservationId)
                                .orElseThrow(() -> {

                                        log.warn(
                                                        "RESERVATION_CANCEL_REJECTED reason=reservation-not-found reservationId={} userId={}",
                                                        reservationId,
                                                        userId);

                                        return new ReservationConflictException(
                                                        "Reservation not found: " +
                                                                        reservationId);
                                });

                if (!reservation.getUser()
                                .getId()
                                .equals(userId)) {

                        log.warn(
                                        "RESERVATION_CANCEL_REJECTED reason=not-owner reservationId={} userId={} ownerId={}",
                                        reservationId,
                                        userId,
                                        reservation.getUser().getId());

                        throw new ReservationConflictException(
                                        "You can cancel only your own reservation");
                }

                if (reservation.getStatus() == Reservation.ReservationStatus.CANCELLED) {

                        log.warn(
                                        "RESERVATION_CANCEL_REJECTED reason=already-cancelled reservationId={} userId={}",
                                        reservationId,
                                        userId);

                        throw new ReservationConflictException(
                                        "Reservation already cancelled");
                }

                List<ReservationSeat> reservationSeats = reservationSeatRepository
                                .findByReservation_Id(
                                                reservationId);

                if (reservationSeats.isEmpty()) {

                        log.error(
                                        "RESERVATION_CANCEL_FAILED reason=no-seats reservationId={} userId={}",
                                        reservationId,
                                        userId);

                        throw new ReservationConflictException(
                                        "No seats found for reservation");
                }

                List<String> seatNumbers = reservationSeats.stream()
                                .map(rs -> rs.getSeat()
                                                .getSeatNumber())
                                .distinct()
                                .sorted()
                                .toList();

                List<Seat> seats = seatRepository.findSeatsForUpdate(
                                reservation.getShow().getId(),
                                seatNumbers);

                if (seats.size() != seatNumbers.size()) {

                        log.error(
                                        "RESERVATION_CANCEL_FAILED reason=seat-not-found reservationId={} userId={} seats={}",
                                        reservationId,
                                        userId,
                                        seatNumbers);

                        throw new ReservationConflictException(
                                        "One or more reservation seats no longer exist");
                }

                for (Seat seat : seats) {
                        seat.setStatus(
                                        Seat.SeatStatus.AVAILABLE);
                }

                reservation.setStatus(
                                Reservation.ReservationStatus.CANCELLED);

                updateAvailableSeats(
                                reservation.getShow().getId(),
                                seatNumbers.size());

                log.info(
                                "RESERVATION_CANCELLED reservationId={} showId={} userId={} seats={}",
                                reservationId,
                                reservation.getShow().getId(),
                                userId,
                                seatNumbers);

                return toResponse(reservation);
        }

        private String createRequestHash(
                        ReserveRequest request) {

                return String.join(
                                ",",
                                request.seats()
                                                .stream()
                                                .distinct()
                                                .sorted()
                                                .toList());
        }

        private void updateAvailableSeats(
                        Long showId,
                        long delta) {

                long available = seatRepository
                                .findByShowIdOrderBySeatNumber(
                                                showId)
                                .stream()
                                .filter(seat -> seat.getStatus() == Seat.SeatStatus.AVAILABLE)
                                .count();

                reservationMetrics.setAvailableSeats(
                                showId,
                                (int) available);
        }

        private ReservationResponse toResponse(
                        Reservation reservation) {

                List<String> seats = reservationSeatRepository
                                .findSeatNumbersByReservationId(
                                                reservation.getId());

                return new ReservationResponse(
                                reservation.getId(),
                                reservation.getShow().getId(),
                                reservation.getUser().getId(),
                                seats,
                                reservation.getAmountPaise(),
                                reservation.getStatus()
                                                .name()
                                                .toLowerCase());
        }
}