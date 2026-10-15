package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Money;
import jakarta.validation.constraints.NotNull;

public record CashPaymentPreviewRequest(@NotNull Money amount) {
}
