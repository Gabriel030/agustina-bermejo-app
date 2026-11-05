package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

import java.time.LocalDate;

public record ReceiptProtectionFacts(InvoicePaymentStatus paymentStatus, LocalDate dueDate, LocalDate uploadDate, LocalDate transferDate) {
}
