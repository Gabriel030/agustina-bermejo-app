package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Objects;

public record InvoiceDraftHeader(
        Long studentCurrentAccountId,
        Currency currency,
        InvoiceDocumentType documentType,
        InvoiceType invoiceType,
        InvoiceIssuingReason issuingReason,
        Long originInvoiceId) {

    public InvoiceDraftHeader {
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(invoiceType, "invoiceType");
        Objects.requireNonNull(issuingReason, "issuingReason");
    }
}
