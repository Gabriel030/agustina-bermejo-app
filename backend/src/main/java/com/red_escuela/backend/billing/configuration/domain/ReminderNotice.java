package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
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

    public LocalDate firstDateFor(ReminderMilestoneDates dates) {
        return dates.dateOf(milestone).plusDays(offsetDays);
    }

    public boolean isBeforeDueDate(ReminderMilestoneDates dates) {
        return isBeforeDueDate(firstDateFor(dates), dates);
    }

    public static boolean isBeforeDueDate(LocalDate occurrenceDate, ReminderMilestoneDates dates) {
        return occurrenceDate.isBefore(dates.dueDate());
    }
}
