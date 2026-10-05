package com.paytm.seatreservation.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record CreateShowRequest(

        @NotBlank String name,

        @NotEmpty List<@NotBlank String> seats,

        @Positive Long price_paise) {
}