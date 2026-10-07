package com.red_escuela.backend.billing.invoice.domain.event;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

public record InvoicePaymentReversedEvent(Long invoiceId, InvoicePaymentStatus resultingStatus) {
}
