package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudentPaymentResponse(Long id, Money amount, Money approvedAmount, Money refundedAmount, String status, String gateway, String methodType,
                                     LocalDate paidOn, LocalDateTime registeredAt, String receiptNumber) {

    public static StudentPaymentResponse from(Payment payment) {
        String receiptNumber = payment.getMethodDetails() instanceof CashPaymentDetails cash ? cash.receiptNumber() : null;
        return new StudentPaymentResponse(payment.getId(), payment.getAmount().value(),
                payment.getApprovedAmount() == null ? null : payment.getApprovedAmount().value(), payment.getRefundedAmount().value(),
                payment.getStatus().name(), payment.getGateway().name(), payment.getMethodType().name(), payment.getPaidOn(), payment.getRegisteredAt(),
                receiptNumber);
    }
}
