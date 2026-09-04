package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PaymentAllocationRequest(@NotNull Long invoiceId, @NotNull BigDecimal amountApplied) {
}
