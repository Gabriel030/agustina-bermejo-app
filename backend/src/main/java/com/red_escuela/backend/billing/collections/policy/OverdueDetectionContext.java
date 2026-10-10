package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

import java.time.LocalDate;

public record OverdueDetectionContext(InvoicePaymentStatus paymentStatus, LocalDate dueDate, int gracePeriodDays, LocalDate today) {

    public LocalDate lastDayWithoutOverdue() {
        return dueDate.plusDays(gracePeriodDays);
    }
}
