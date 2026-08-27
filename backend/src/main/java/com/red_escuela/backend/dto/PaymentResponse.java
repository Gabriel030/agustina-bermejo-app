package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(Long id, Long invoiceId, BigDecimal amount, LocalDateTime paidAt, String method) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getInvoice().getId(), payment.getAmount(),
                payment.getPaidAt(), payment.getMethod().name());
    }
}
