package com.red_escuela.backend.billing.invoice.domain.event;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;

import java.time.LocalDate;

public record InvoicePaidEvent(Long invoiceId, Long studentCurrentAccountId, InvoicePaymentEvent cause, LocalDate businessDate) {
}
