package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmProspectRequest(
        @NotBlank String dni,
        @NotBlank String street,
        String number,
        String floor,
        @NotBlank String city,
        String state,
        String postalCode,
        String country) {
}
