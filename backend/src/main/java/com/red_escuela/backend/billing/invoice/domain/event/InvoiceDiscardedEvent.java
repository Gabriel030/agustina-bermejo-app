package com.red_escuela.backend.billing.invoice.domain.event;

public record InvoiceDiscardedEvent(Long invoiceId, String reason) {
}
