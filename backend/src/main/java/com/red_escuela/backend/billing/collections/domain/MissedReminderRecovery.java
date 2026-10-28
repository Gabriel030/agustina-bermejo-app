package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.time.LocalDate;
import java.util.Set;

public record MissedReminderRecovery(Set<LocalDate> missedDays, Set<FiredReminderOccurrence> alreadyHandled) {

    public MissedReminderRecovery {
        missedDays = Set.copyOf(missedDays);
        alreadyHandled = Set.copyOf(alreadyHandled);
    }

    public static MissedReminderRecovery none() {
        return new MissedReminderRecovery(Set.of(), Set.of());
    }

    public static MissedReminderRecovery ofMissedDays(Set<LocalDate> missedDays) {
        return new MissedReminderRecovery(missedDays, Set.of());
    }

    public boolean isRecovering() {
        return !missedDays.isEmpty();
    }

    public MissedReminderRecovery withAlreadyHandled(Set<FiredReminderOccurrence> handled) {
        return new MissedReminderRecovery(missedDays, handled);
    }

    public boolean recovers(ReminderOccurrence occurrence, ReminderChannel channel, LocalDate firingDate, LocalDate businessDate) {
        return missedDays.contains(firingDate)
                && stillMakesSenseOn(occurrence.messageType(), occurrence.dueDate(), businessDate)
                && !alreadyHandled.contains(new FiredReminderOccurrence(occurrence.invoiceId(), occurrence.noticeKey(), channel, firingDate));
    }

    private static boolean stillMakesSenseOn(ReminderMessageType messageType, LocalDate dueDate, LocalDate businessDate) {
        return switch (messageType) {
            case AFTER_DUE_REMINDER, BEFORE_SUSPENSION_REMINDER -> true;
            case ISSUANCE_NOTICE, AFTER_ISSUE_REMINDER, BEFORE_DUE_REMINDER -> !businessDate.isAfter(dueDate);
        };
    }
}
