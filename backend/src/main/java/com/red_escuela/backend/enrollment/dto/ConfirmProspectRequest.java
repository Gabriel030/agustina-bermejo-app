package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.constraints.NotBlank;

public record ConfirmProspectRequest(
        @NotBlank String dni,
        DocumentType documentType,
        @NotBlank String street,
        String number,
        String floor,
        @NotBlank String city,
        String state,
        String postalCode,
        String country) {
}
