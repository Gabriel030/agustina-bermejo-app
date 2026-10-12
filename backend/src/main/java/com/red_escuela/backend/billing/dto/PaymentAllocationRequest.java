package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.PositiveMoney;

public record PaymentAllocationRequest(
        @NotNull Long invoiceId,
        @NotNull @PositiveMoney Money amountApplied) {
}
