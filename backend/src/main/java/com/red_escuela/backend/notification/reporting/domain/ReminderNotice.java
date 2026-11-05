package com.red_escuela.backend.notification.reporting.domain;

import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;

import java.util.Arrays;

public enum ReminderNotice {
    ISSUANCE_NOTICE,
    AFTER_ISSUE_REMINDER,
    BEFORE_DUE_REMINDER,
    AFTER_DUE_REMINDER,
    BEFORE_SUSPENSION_REMINDER,
    DEBT_SUMMARY,
    POST_WITHDRAWAL_SUMMARY,
    OTHER;

    private static final String POST_WITHDRAWAL_SUMMARY_KIND = "POST_WITHDRAWAL_SUMMARY";
    private static final String KIND_SEPARATOR = ":";

    public boolean isAlwaysReported() {
        return this != POST_WITHDRAWAL_SUMMARY && this != OTHER;
    }

    public static ReminderNotice ofDeliveryKind(String kind) {
        if (POST_WITHDRAWAL_SUMMARY_KIND.equals(kind)) {
            return POST_WITHDRAWAL_SUMMARY;
        }
        String[] parts = kind.split(KIND_SEPARATOR, 2);
        if (parts.length == 2 && PlannedReminderFormat.DEBT_SUMMARY.name().equals(parts[0])) {
            return DEBT_SUMMARY;
        }
        if (parts.length == 2 && Arrays.stream(PlannedReminderNoticeType.values()).anyMatch(type -> type.name().equals(parts[1]))) {
            return valueOf(parts[1]);
        }
        return OTHER;
    }
}
