package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;

public record InvoicePaymentShare(Long paymentId, String status, String method, String cardType, Integer cardInstallments, Money amountApplied,
                                  Money paymentTotal, LocalDateTime paidAt, String externalReference, String notes, String registeredByName) {

    public static final String CONFIRMED = "CONFIRMED";

    public boolean isConfirmed() {
        return CONFIRMED.equals(status);
    }
}
