package com.red_escuela.backend.billing.invoice.domain.event;

import java.time.LocalDate;

public record InvoiceMarkedAsOverdueEvent(Long invoiceId, LocalDate originalDueDate, LocalDate businessDate) {
}
