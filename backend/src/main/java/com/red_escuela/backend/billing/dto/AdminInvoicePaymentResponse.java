package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminInvoicePaymentResponse(
        Long paymentId, String status, String method, String cardType, Integer cardInstallments,
        Money amountApplied, Money paymentTotal, LocalDateTime paidAt, String externalReference,
        String notes, String registeredByName, Long receiptId) {

    public static AdminInvoicePaymentResponse from(InvoicePaymentShare share, Map<Long, Long> receiptIdByPaymentId) {
        return new AdminInvoicePaymentResponse(
                share.paymentId(), share.status(), share.method(), share.cardType(), share.cardInstallments(),
                share.amountApplied(), share.paymentTotal(), share.paidAt(), share.externalReference(),
                share.notes(), share.registeredByName(), receiptIdByPaymentId.get(share.paymentId()));
    }
}
