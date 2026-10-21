package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ReminderOccurrenceExpander {

    private ReminderOccurrenceExpander() {
    }

    public static List<ReminderOccurrence> expandOccurrencesOf(ReminderInvoiceFact invoice, List<ReminderNotice> notices) {
        List<ReminderOccurrence> occurrences = new ArrayList<>();
        for (ReminderNotice notice : notices) {
            for (LocalDate date : notice.occurrenceDatesFor(invoice.milestoneDates())) {
                if (!date.isBefore(invoice.issueDate())) {
                    occurrences.add(new ReminderOccurrence(invoice.invoiceId(), notice.noticeKey(), notice.messageType(), notice.channels(), date, invoice.dueDate()));
                }
            }
        }
        occurrences.sort(Comparator.comparing(ReminderOccurrence::date).thenComparing(ReminderOccurrence::noticeKey));
        return occurrences;
    }
}
