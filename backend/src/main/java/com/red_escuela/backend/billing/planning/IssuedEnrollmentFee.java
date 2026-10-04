package com.red_escuela.backend.billing.planning;

import com.red_escuela.backend.billing.entity.InvoiceStatus;

import java.time.LocalDate;
import java.time.YearMonth;

public record IssuedEnrollmentFee(Long invoiceId, Long templateId, Integer slot, YearMonth period, InvoiceStatus status, LocalDate dueDate) {

    public boolean isCancelled() {
        return status == InvoiceStatus.CANCELLED;
    }

    public boolean isSettled() {
        return status == InvoiceStatus.PAID || status == InvoiceStatus.WAIVED;
    }

    public boolean covers(YearCycle cycle, int cycleSlot) {
        if (templateId != null && slot != null) {
            return templateId.equals(cycle.templateId()) && slot == cycleSlot;
        }
        return cycle.slotContains(cycleSlot, period);
    }
}
