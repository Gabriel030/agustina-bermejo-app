package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ReminderOccurrence(Long invoiceId, String noticeKey, ReminderMessageType messageType, Set<ReminderChannel> channels,
                                 LocalDate date, LocalDate dueDate) {

    public ReminderOccurrence {
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(noticeKey, "noticeKey");
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(dueDate, "dueDate");
        channels = channels == null || channels.isEmpty() ? EnumSet.noneOf(ReminderChannel.class) : EnumSet.copyOf(channels);
    }

    public boolean sendsEmail() {
        return channels.contains(ReminderChannel.EMAIL);
    }

    public boolean sendsInApp() {
        return channels.contains(ReminderChannel.IN_APP);
    }

    public boolean inAppPrecedesDueDate() {
        return sendsInApp() && date.isBefore(dueDate);
    }

    public boolean emailPrecedesDueDate(NonBusinessDays nonBusinessDays) {
        return emailDate(nonBusinessDays).map(sendDate -> sendDate.isBefore(dueDate)).orElse(false);
    }

    public Optional<LocalDate> inAppDate() {
        return sendsInApp() ? Optional.of(date) : Optional.empty();
    }

    public Optional<LocalDate> emailDate(NonBusinessDays nonBusinessDays) {
        return sendsEmail() ? Optional.of(ReminderEmailDateShifter.shiftEmailDate(date, dueDate, nonBusinessDays)) : Optional.empty();
    }
}
