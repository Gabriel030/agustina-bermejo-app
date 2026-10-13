package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReminderEmailCapTrimmer {

    private ReminderEmailCapTrimmer() {
    }

    public static List<TrimmedReminder> trimmedBy(List<ReminderNotice> notices, CollectionReminderSettings settings, ReminderMilestoneDates dates) {
        List<Occurrence> occurrences = cappedEmailOccurrences(notices, dates);
        List<TrimmedReminder> trimmed = new ArrayList<>();
        Map<YearMonth, Integer> keptPerMonth = new HashMap<>();
        LocalDate lastKept = null;
        for (Occurrence occurrence : occurrences) {
            LocalDate earliest = lastKept == null ? occurrence.date() : max(occurrence.date(), lastKept.plusDays(settings.minDaysBetweenEmails()));
            if (earliest.isAfter(occurrence.deadline())) {
                trimmed.add(new TrimmedReminder(occurrence.noticeKey(), occurrence.date(), TrimmedReminder.TrimReason.MIN_SPACING));
            } else if (keptPerMonth.getOrDefault(YearMonth.from(earliest), 0) >= settings.maxReminderEmailsPerMonth()) {
                trimmed.add(new TrimmedReminder(occurrence.noticeKey(), occurrence.date(), TrimmedReminder.TrimReason.MONTHLY_CAP));
            } else {
                keptPerMonth.merge(YearMonth.from(earliest), 1, Integer::sum);
                lastKept = earliest;
            }
        }
        return trimmed;
    }

    private static List<Occurrence> cappedEmailOccurrences(List<ReminderNotice> notices, ReminderMilestoneDates dates) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (ReminderNotice notice : notices) {
            if (!notice.sendsEmail() || notice.milestone() == ReminderMilestone.SUSPENSION) {
                continue;
            }
            LocalDate first = notice.firstDateFor(dates);
            LocalDate deadline = deadlineOf(notice, dates);
            occurrences.add(new Occurrence(notice.noticeKey(), first, deadline));
            if (notice.repetition() != null && notice.repetition().everyDays() > 0) {
                LocalDate until = dates.dateOf(notice.repetition().until());
                for (LocalDate next = first.plusDays(notice.repetition().everyDays()); next.isBefore(until); next = next.plusDays(notice.repetition().everyDays())) {
                    occurrences.add(new Occurrence(notice.noticeKey(), next, deadline));
                }
            }
        }
        occurrences.sort(Comparator.comparing(Occurrence::date).thenComparing(Occurrence::noticeKey));
        return occurrences;
    }

    private static LocalDate deadlineOf(ReminderNotice notice, ReminderMilestoneDates dates) {
        return notice.isBeforeDueDate(dates) ? dates.dueDate().minusDays(1) : dates.suspensionDate().minusDays(1);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private record Occurrence(String noticeKey, LocalDate date, LocalDate deadline) {
    }
}
