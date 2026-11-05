package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.Map;

public record MeInvoicePaymentResponse(
        Long paymentId, String method, String cardType, Integer cardInstallments,
        Money amountApplied, Money paymentTotal, LocalDateTime paidAt, String externalReference, Long receiptId) {

    public static MeInvoicePaymentResponse from(InvoicePaymentShare share, Map<Long, Long> receiptIdByPaymentId) {
        return new MeInvoicePaymentResponse(
                share.paymentId(), share.method(), share.cardType(), share.cardInstallments(),
                share.amountApplied(), share.paymentTotal(), share.paidAt(), share.externalReference(),
                receiptIdByPaymentId.get(share.paymentId()));
    }
}
