package com.paytm.seatreservation.dto;

import java.util.List;

public record ShowResponse(
        Long id,
        String name,
        Long price_paise,
        Integer per_user_limit,
        Integer total_seats,
        Integer available,
        Integer held,
        Integer confirmed,
        List<SeatResponse> seats) {
}