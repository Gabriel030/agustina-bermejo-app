package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProgramYearBillingTemplateRequest(@NotNull Long termId, @NotNull @Positive Integer recurringFeeInvoicesCount) {
}
