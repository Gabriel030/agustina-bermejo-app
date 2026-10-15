package com.red_escuela.backend.billing.invoice.domain.event;

import java.time.LocalDate;

public record InvoicePaymentReviewReleasedEvent(Long invoiceId, LocalDate businessDate) {
}
