package com.paytm.seatreservation.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paytm.seatreservation.dto.ReservationResponse;
import com.paytm.seatreservation.service.ReservationService;

@RestController
@RequestMapping("/reservations")
public class ReservationCancellationController {

    private final ReservationService reservationService;

    public ReservationCancellationController(
            ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID reservationId,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        ReservationResponse response = reservationService.cancel(reservationId, userId);

        return ResponseEntity.ok(response);
    }
}