package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;

import java.util.Collection;
import java.util.Comparator;

public final class ReminderMessageSeverity {

    public static final int DEBT_SUMMARY_DISPATCH_PRIORITY = 3;

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

    public static int dispatchPriorityOf(ReminderMessageType messageType) {
        return switch (messageType) {
            case BEFORE_SUSPENSION_REMINDER -> 1;
            case AFTER_DUE_REMINDER -> 2;
            case BEFORE_DUE_REMINDER -> 4;
            case AFTER_ISSUE_REMINDER -> 5;
            case ISSUANCE_NOTICE -> 6;
        };
    }

    public static int dispatchPriorityOf(ReminderMessageType leadMessageType, ReminderMessageFormat format) {
        int leadPriority = dispatchPriorityOf(leadMessageType);
        return format == ReminderMessageFormat.DEBT_SUMMARY ? Math.min(leadPriority, DEBT_SUMMARY_DISPATCH_PRIORITY) : leadPriority;
    }

    public static ReminderMessageType mostSevereOf(Collection<ReminderMessageType> messageTypes) {
        return messageTypes.stream().max(Comparator.comparingInt(ReminderMessageSeverity::rankOf)).orElseThrow();
    }
}
