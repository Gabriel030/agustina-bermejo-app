package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceBreakdownPaymentResponse(Long paymentId, BigDecimal amount, String method, LocalDateTime paidAt) {
}
