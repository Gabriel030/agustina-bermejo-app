package com.red_escuela.backend.billing.events;

public enum PlannedReminderNoticeType {
    ISSUANCE_NOTICE(5),
    AFTER_ISSUE_REMINDER(4),
    BEFORE_DUE_REMINDER(3),
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
