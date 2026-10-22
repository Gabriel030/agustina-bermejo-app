package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record PlannedReminderLine(Long invoiceId, Long originInvoiceId, PlannedReminderInvoiceKind invoiceKind, boolean debitNote, LocalDate dueDate, Money balance,
                                  boolean receiptUnderReview, boolean precedesDueDate, List<PlannedReminderNotice> notices, Long scheduleVersionId,
                                  Long exceptionVersionId, Long groupVersionId, Long globalSettingsVersionId) {

    public PlannedReminderLine {
        notices = List.copyOf(notices);
    }

    public boolean isHighlighted() {
        return !notices.isEmpty();
    }
}
