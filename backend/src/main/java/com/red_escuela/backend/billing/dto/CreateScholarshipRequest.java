package com.red_escuela.backend.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateScholarshipRequest(
        @Valid @NotNull MonetaryAdjustmentValueRequest value,
        @NotBlank String reason,
        @NotNull @Min(1) Integer installments) {
}
