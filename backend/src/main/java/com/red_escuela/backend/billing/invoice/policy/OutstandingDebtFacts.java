package com.red_escuela.backend.billing.invoice.policy;

public record OutstandingDebtFacts(Long studentId, boolean hasOverdueInvoice, boolean hasOpenInvoiceOfWithdrawnEnrollment,
                                   boolean hasOpenInvoiceOfWithdrawnEnrollmentInPendingRequest) {
}
