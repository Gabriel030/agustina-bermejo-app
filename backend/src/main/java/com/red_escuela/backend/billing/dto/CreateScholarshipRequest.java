package com.red_escuela.backend.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.billing.entity.ScholarshipScope;

public record CreateScholarshipRequest(
        @Valid @NotNull MonetaryAdjustmentRequest value,
        @NotBlank String reason,
        @NotNull @Min(1) Integer installments,
        ScholarshipScope appliesTo) {

    public CreateScholarshipRequest(MonetaryAdjustmentRequest value, String reason, Integer installments) {
        this(value, reason, installments, null);
    }
}
