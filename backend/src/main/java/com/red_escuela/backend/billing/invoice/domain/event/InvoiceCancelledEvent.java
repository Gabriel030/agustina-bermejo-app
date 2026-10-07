package com.red_escuela.backend.billing.invoice.domain.event;

import java.time.LocalDate;

public record InvoiceCancelledEvent(Long invoiceId, LocalDate businessDate) {
}
