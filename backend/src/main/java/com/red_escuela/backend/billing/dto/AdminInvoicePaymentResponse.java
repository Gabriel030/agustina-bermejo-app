package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminInvoicePaymentResponse(
        Long paymentId, String status, String method, String cardType, Integer cardInstallments,
        Money amountApplied, Money paymentTotal, LocalDateTime paidAt, String externalReference,
        String notes, String registeredByName, Long receiptId) {

    public static AdminInvoicePaymentResponse from(PaymentAllocation allocation, Map<Long, Long> receiptIdByPaymentId) {
        Payment payment = allocation.getPayment();
        return new AdminInvoicePaymentResponse(
                payment.getId(), payment.getStatus().name(), payment.getMethod().name(),
                payment.getCardType() == null ? null : payment.getCardType().name(), payment.getCardInstallments(),
                allocation.getAmountApplied(), payment.getAmount(), payment.getPaidAt(), payment.getExternalReference(),
                payment.getNotes(),
                payment.getRegisteredBy().getName() + " " + payment.getRegisteredBy().getLastname(),
                receiptIdByPaymentId.get(payment.getId()));
    }
}
