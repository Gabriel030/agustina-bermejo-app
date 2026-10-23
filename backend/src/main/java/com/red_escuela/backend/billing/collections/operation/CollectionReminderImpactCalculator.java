package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderOccurrence;
import com.red_escuela.backend.billing.collections.domain.ReminderOccurrenceExpander;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CollectionReminderImpactCalculator {

    private CollectionReminderImpactCalculator() {
    }

    public static int countStudentsWithChangedSchedule(List<ReminderStudentFacts> students, Map<Long, ResolvedReminderSchedule> currentByInvoiceId,
                                                       Map<Long, ResolvedReminderSchedule> proposedByInvoiceId, ReminderDigestMode currentMode,
                                                       ReminderDigestMode proposedMode, LocalDate windowStart, LocalDate windowEnd, NonBusinessDays nonBusinessDays) {
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("The simulation window ends before it starts");
        }
        int changed = 0;
        for (ReminderStudentFacts student : students) {
            Set<FiringReminder> current = firingRemindersOf(student, currentByInvoiceId, windowStart, windowEnd, nonBusinessDays);
            Set<FiringReminder> proposed = firingRemindersOf(student, proposedByInvoiceId, windowStart, windowEnd, nonBusinessDays);
            boolean modeChangesTheMessages = currentMode != proposedMode && !(current.isEmpty() && proposed.isEmpty());
            if (!current.equals(proposed) || modeChangesTheMessages) {
                changed++;
            }
        }
        return changed;
    }

    private static Set<FiringReminder> firingRemindersOf(ReminderStudentFacts student, Map<Long, ResolvedReminderSchedule> schedulesByInvoiceId, LocalDate windowStart,
                                                         LocalDate windowEnd, NonBusinessDays nonBusinessDays) {
        Set<FiringReminder> firing = new HashSet<>();
        for (ReminderInvoiceFact fact : student.invoices()) {
            ResolvedReminderSchedule schedule = schedulesByInvoiceId.get(fact.invoiceId());
            if (schedule == null || !DailyReminderDigestPlanner.sendsReminders(fact)) {
                continue;
            }
            for (ReminderOccurrence occurrence : ReminderOccurrenceExpander.expandOccurrencesUpTo(fact, schedule.notices(), windowEnd)) {
                occurrence.inAppDate().filter(date -> isWithin(date, windowStart, windowEnd))
                        .ifPresent(date -> firing.add(new FiringReminder(occurrence.invoiceId(), occurrence.noticeKey(), ReminderChannel.IN_APP, date)));
                occurrence.emailDate(nonBusinessDays).filter(date -> isWithin(date, windowStart, windowEnd))
                        .ifPresent(date -> firing.add(new FiringReminder(occurrence.invoiceId(), occurrence.noticeKey(), ReminderChannel.EMAIL, date)));
            }
        }
        return firing;
    }

    private static boolean isWithin(LocalDate date, LocalDate windowStart, LocalDate windowEnd) {
        return !date.isBefore(windowStart) && !date.isAfter(windowEnd);
    }

    private record FiringReminder(Long invoiceId, String noticeKey, ReminderChannel channel, LocalDate date) {
    }
}
