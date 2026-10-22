package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ReminderOccurrenceExpander {

    private ReminderOccurrenceExpander() {
    }

    public static List<ReminderOccurrence> expandOccurrencesOf(ReminderInvoiceFact invoice, List<ReminderNotice> notices) {
        return expandOccurrencesBetween(invoice, notices, null);
    }

    public static List<ReminderOccurrence> expandOccurrencesUpTo(ReminderInvoiceFact invoice, List<ReminderNotice> notices, LocalDate horizon) {
        return expandOccurrencesBetween(invoice, notices, Objects.requireNonNull(horizon, "horizon"));
    }

    private static List<ReminderOccurrence> expandOccurrencesBetween(ReminderInvoiceFact invoice, List<ReminderNotice> notices, LocalDate horizon) {
        List<ReminderOccurrence> occurrences = new ArrayList<>();
        for (ReminderNotice notice : notices) {
            for (LocalDate date : occurrenceDatesOf(invoice, notice, horizon)) {
                if (!date.isBefore(invoice.issueDate())) {
                    occurrences.add(new ReminderOccurrence(invoice.invoiceId(), notice.noticeKey(), notice.messageType(), notice.channels(), recoveredDateOf(invoice, date), invoice.dueDate()));
                }
            }
        }
        occurrences.sort(Comparator.comparing(ReminderOccurrence::date).thenComparing(ReminderOccurrence::noticeKey));
        return occurrences;
    }

    private static List<LocalDate> occurrenceDatesOf(ReminderInvoiceFact invoice, ReminderNotice notice, LocalDate horizon) {
        boolean openEnded = horizon != null && notice.repeatsUntilSuspension() && invoice.suspensionDate() == null;
        if (!openEnded) {
            return notice.occurrenceDatesFor(invoice.milestoneDates());
        }
        if (invoice.enrollmentSuspended()) {
            return notice.firstDateFor(invoice.milestoneDates()).map(List::of).orElse(List.of());
        }
        return notice.openEndedOccurrenceDatesFor(invoice.milestoneDates(), horizon);
    }

    private static LocalDate recoveredDateOf(ReminderInvoiceFact invoice, LocalDate date) {
        return invoice.issuedAfterPreviousRun() && date.equals(invoice.issueDate()) ? date.plusDays(1) : date;
    }
}
