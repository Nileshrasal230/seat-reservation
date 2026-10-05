package com.paytm.seatreservation.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank String username,

        @NotBlank String password) {
}