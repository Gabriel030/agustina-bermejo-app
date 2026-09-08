package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBillingPeriodRequest(@NotNull Integer startDay, @NotNull Integer endDay, @NotNull LocalDate effectiveFrom) {
}
