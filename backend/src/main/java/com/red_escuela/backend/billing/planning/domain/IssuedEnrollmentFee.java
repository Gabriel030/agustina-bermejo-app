package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;

import java.time.LocalDate;
import java.time.YearMonth;

public record IssuedEnrollmentFee(Long invoiceId, Long templateId, Integer slot, YearMonth period, InvoicePaymentStatus status, LocalDate dueDate) {

    public boolean isCancelled() {
        return status == InvoicePaymentStatus.CANCELLED;
    }

    public boolean isSettled() {
        return status == InvoicePaymentStatus.PAID;
    }

    public boolean covers(YearCycle cycle, int cycleSlot) {
        if (templateId != null && slot != null) {
            return templateId.equals(cycle.templateId()) && slot == cycleSlot;
        }
        return cycle.slotContains(cycleSlot, period);
    }
}
