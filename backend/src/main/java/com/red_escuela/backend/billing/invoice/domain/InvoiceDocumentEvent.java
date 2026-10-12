package com.red_escuela.backend.billing.invoice.domain;

public enum InvoiceDocumentEvent {
    DRAFT_CREATED,
    DRAFT_CONTENT_CHANGED,
    DRAFT_SUBMITTED_FOR_PAYMENT,
    INVOICE_ISSUED,
    DRAFT_DISCARDED
}
