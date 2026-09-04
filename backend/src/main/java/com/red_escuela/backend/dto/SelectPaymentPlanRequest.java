package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

// scopeMonths is only required if the chosen option is UPFRONT — validated in the service.
public record SelectPaymentPlanRequest(@NotNull Long paymentPlanOptionId, Integer scopeMonths) {
}
