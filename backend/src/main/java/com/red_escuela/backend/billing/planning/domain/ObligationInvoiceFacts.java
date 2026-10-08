package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.entity.InvoiceStatus;

public record ObligationInvoiceFacts(InvoiceStatus status, boolean receiptUnderReview, boolean untouched) {
}
