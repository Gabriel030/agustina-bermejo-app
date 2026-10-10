package com.red_escuela.backend.billing.note.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

public enum CreditNoteDestination {
    DEBT_OFFSET,
    FREE_CREDIT;

    public static CreditNoteDestination ofInvoiceStatus(InvoicePaymentStatus status) {
        return status == InvoicePaymentStatus.PAID ? FREE_CREDIT : DEBT_OFFSET;
    }
}
