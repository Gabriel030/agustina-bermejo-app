package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ReminderNotice(String noticeKey, ReminderMessageType messageType, ReminderMilestone milestone, int offsetDays,
                             Set<ReminderChannel> channels, ReminderRepetition repetition) {

    public ReminderNotice {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(milestone, "milestone");
        noticeKey = noticeKey == null || noticeKey.isBlank() ? UUID.randomUUID().toString() : noticeKey;
        channels = channels == null || channels.isEmpty() ? EnumSet.noneOf(ReminderChannel.class) : EnumSet.copyOf(channels);
    }

    public static ReminderNotice of(String noticeKey, ReminderMessageType messageType, ReminderMilestone milestone, int offsetDays,
                                    Collection<ReminderChannel> channels) {
        return new ReminderNotice(noticeKey, messageType, milestone, offsetDays, EnumSet.copyOf(channels), null);
    }

    public boolean sendsEmail() {
        return channels.contains(ReminderChannel.EMAIL);
    }

    public Optional<LocalDate> firstDateFor(ReminderMilestoneDates dates) {
        return dates.dateOf(milestone).map(date -> date.plusDays(offsetDays));
    }

    public boolean isBeforeDueDate(ReminderMilestoneDates dates) {
        return firstDateFor(dates).map(first -> isBeforeDueDate(first, dates)).orElse(false);
    }

    public static boolean isBeforeDueDate(LocalDate occurrenceDate, ReminderMilestoneDates dates) {
        return occurrenceDate.isBefore(dates.dueDate());
    }

    public boolean repeatsUntilSuspension() {
        return repetition != null && repetition.everyDays() > 0 && repetition.until() == ReminderMilestone.SUSPENSION;
    }

    public List<LocalDate> openEndedOccurrenceDatesFor(ReminderMilestoneDates dates, LocalDate horizon) {
        Optional<LocalDate> first = firstDateFor(dates);
        if (first.isEmpty() || !repeatsUntilSuspension()) {
            return first.map(List::of).orElse(List.of());
        }
        List<LocalDate> occurrences = new ArrayList<>();
        occurrences.add(first.get());
        for (LocalDate next = first.get().plusDays(repetition.everyDays()); !next.isAfter(horizon); next = next.plusDays(repetition.everyDays())) {
            occurrences.add(next);
        }
        return occurrences;
    }

    public List<LocalDate> occurrenceDatesFor(ReminderMilestoneDates dates) {
        Optional<LocalDate> first = firstDateFor(dates);
        if (first.isEmpty()) {
            return List.of();
        }
        List<LocalDate> occurrences = new ArrayList<>();
        occurrences.add(first.get());
        Optional<LocalDate> until = repetition == null || repetition.everyDays() <= 0 ? Optional.empty() : dates.dateOf(repetition.until());
        if (until.isPresent()) {
            for (LocalDate next = first.get().plusDays(repetition.everyDays()); next.isBefore(until.get()); next = next.plusDays(repetition.everyDays())) {
                occurrences.add(next);
            }
        }
        return occurrences;
    }
}
