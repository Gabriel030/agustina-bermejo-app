package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.Collection;
import java.util.Comparator;

public final class ReminderMessageSeverity {

    private ReminderMessageSeverity() {
    }

    public static int rankOf(ReminderMessageType messageType) {
        return switch (messageType) {
            case ISSUANCE_NOTICE -> 1;
            case AFTER_ISSUE_REMINDER -> 2;
            case BEFORE_DUE_REMINDER -> 3;
            case AFTER_DUE_REMINDER -> 4;
            case BEFORE_SUSPENSION_REMINDER -> 5;
        };
    }

    public static ReminderMessageType mostSevereOf(Collection<ReminderMessageType> messageTypes) {
        return messageTypes.stream().max(Comparator.comparingInt(ReminderMessageSeverity::rankOf)).orElseThrow();
    }
}
