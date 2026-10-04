package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.Map;

public record MeInvoicePaymentResponse(
        Long paymentId, String method, String cardType, Integer cardInstallments,
        Money amountApplied, Money paymentTotal, LocalDateTime paidAt, String externalReference, Long receiptId) {

    public static MeInvoicePaymentResponse from(PaymentAllocation allocation, Map<Long, Long> receiptIdByPaymentId) {
        Payment payment = allocation.getPayment();
        return new MeInvoicePaymentResponse(
                payment.getId(), payment.getMethod().name(),
                payment.getCardType() == null ? null : payment.getCardType().name(), payment.getCardInstallments(),
                allocation.getAmountApplied(), payment.getAmount(), payment.getPaidAt(), payment.getExternalReference(),
                receiptIdByPaymentId.get(payment.getId()));
    }
}
