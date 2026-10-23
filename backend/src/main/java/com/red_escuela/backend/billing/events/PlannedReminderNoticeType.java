package com.red_escuela.backend.billing.events;

public enum PlannedReminderNoticeType {
    ISSUANCE_NOTICE(6),
    AFTER_ISSUE_REMINDER(5),
    BEFORE_DUE_REMINDER(4),
    AFTER_DUE_REMINDER(2),
    BEFORE_SUSPENSION_REMINDER(1);

    private final int dispatchPriority;

    PlannedReminderNoticeType(int dispatchPriority) {
        this.dispatchPriority = dispatchPriority;
    }

    public int dispatchPriority() {
        return dispatchPriority;
    }
}
