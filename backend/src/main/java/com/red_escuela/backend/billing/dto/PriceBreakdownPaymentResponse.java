package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDateTime;

public record PriceBreakdownPaymentResponse(Long paymentId, Money amount, String method, LocalDateTime paidAt) {
}
