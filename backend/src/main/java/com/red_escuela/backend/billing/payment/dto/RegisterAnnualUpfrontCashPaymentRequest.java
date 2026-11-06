package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RegisterAnnualUpfrontCashPaymentRequest(@NotNull Money amount, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String receiptNumber) {
}
