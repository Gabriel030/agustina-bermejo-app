package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

public record AdvanceBillingYearRequest(@NotNull Long templateId) {
}
