package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

public record ObligationInvoiceFacts(InvoicePaymentStatus status, boolean receiptUnderReview, boolean untouched) {
}
