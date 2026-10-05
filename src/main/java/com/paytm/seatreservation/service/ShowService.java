package com.paytm.seatreservation.service;

import com.paytm.seatreservation.dto.CreateShowRequest;
import com.paytm.seatreservation.dto.SeatResponse;
import com.paytm.seatreservation.dto.ShowResponse;
import com.paytm.seatreservation.entity.Seat;
import com.paytm.seatreservation.entity.Show;
import com.paytm.seatreservation.repository.SeatRepository;
import com.paytm.seatreservation.repository.ShowRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ReservationMetrics reservationMetrics;

    public ShowService(
            ShowRepository showRepository,
            SeatRepository seatRepository,
            ReservationMetrics reservationMetrics) {

        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.reservationMetrics = reservationMetrics;
    }

    // =============================================================
    // CREATE SHOW
    // =============================================================

    @Transactional
    public Show createShow(
            CreateShowRequest request) {

        Show show = Show.builder()
                .name(request.name())
                .pricePaise(
                        request.price_paise())
                .perUserLimit(4)
                .build();

        Show savedShow = showRepository.save(show);

        for (String seatNumber : request.seats()) {

            Seat seat = Seat.builder()
                    .show(savedShow)
                    .seatNumber(seatNumber)
                    .status(
                            Seat.SeatStatus.AVAILABLE)
                    .build();

            seatRepository.save(seat);
        }

        /*
         * Initially every seat is available.
         */
        reservationMetrics.setAvailableSeats(
                savedShow.getId(),
                request.seats().size());

        return savedShow;
    }

    // =============================================================
    // GET SHOW
    // =============================================================

    @Transactional
    public ShowResponse getShow(
            Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException(
                        "Show not found: " +
                                showId));

        List<Seat> seats = seatRepository
                .findByShowIdOrderBySeatNumber(
                        showId);

        int available = 0;
        int held = 0;
        int confirmed = 0;

        List<SeatResponse> seatResponses = seats.stream()
                .map(seat -> {

                    return new SeatResponse(
                            seat.getSeatNumber(),
                            seat.getStatus()
                                    .name()
                                    .toLowerCase());
                })
                .toList();

        for (Seat seat : seats) {

            switch (seat.getStatus()) {

                case AVAILABLE:
                    available++;
                    break;

                case HELD:
                    held++;
                    break;

                case CONFIRMED:
                    confirmed++;
                    break;
            }
        }

        /*
         * Keep gauge synchronized with actual DB state.
         */
        reservationMetrics.setAvailableSeats(
                showId,
                available);

        return new ShowResponse(
                show.getId(),
                show.getName(),
                show.getPricePaise(),
                show.getPerUserLimit(),
                seats.size(),
                available,
                held,
                confirmed,
                seatResponses);
    }
}