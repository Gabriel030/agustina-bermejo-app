package com.red_escuela.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateScholarshipRequest(
        @Valid @NotNull MonetaryAdjustmentValueRequest value,
        @NotBlank String reason,
        @NotNull LocalDate validFrom,
        LocalDate validUntil) {
}
