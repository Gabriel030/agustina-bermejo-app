package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        @NotNull Long invoiceId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amountApplied) {
}
