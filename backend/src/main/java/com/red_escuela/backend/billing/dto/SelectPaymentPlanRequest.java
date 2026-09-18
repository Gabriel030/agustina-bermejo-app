package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

public record SelectPaymentPlanRequest(@NotNull Long paymentPlanOptionId, Integer scopeMonths) {
}
