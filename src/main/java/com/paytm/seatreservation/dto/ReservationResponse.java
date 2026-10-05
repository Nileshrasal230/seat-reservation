package com.paytm.seatreservation.dto;

import java.util.List;
import java.util.UUID;

public record ReservationResponse(

        UUID reservation_id,

        Long show_id,

        Long user_id,

        List<String> seats,

        Long amount_paise,

        String status) {
}