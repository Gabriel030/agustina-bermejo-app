package com.red_escuela.backend.billing.configuration.domain;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record ReminderSchedule(boolean followsGroupSchedule, List<ReminderNotice> notices, ReminderSendingOverrides sendingOverrides) {
    private static final Comparator<ReminderNotice> TIMELINE_ORDER =
            Comparator.comparing(ReminderNotice::milestone).thenComparingInt(ReminderNotice::offsetDays);

    public ReminderSchedule {
        notices = notices == null ? List.of() : List.copyOf(notices);
        sendingOverrides = Objects.requireNonNullElse(sendingOverrides, ReminderSendingOverrides.none());
    }

    public static ReminderSchedule initial() {
        return new ReminderSchedule(false, List.of(
                ReminderNotice.of("issuance", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 0, EnumSet.of(ReminderChannel.IN_APP)),
                ReminderNotice.of("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 5, EnumSet.allOf(ReminderChannel.class)),
                ReminderNotice.of("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1, EnumSet.allOf(ReminderChannel.class)),
                ReminderNotice.of("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 10, EnumSet.allOf(ReminderChannel.class)),
                ReminderNotice.of("before-suspension", ReminderMessageType.BEFORE_SUSPENSION_REMINDER, ReminderMilestone.SUSPENSION, -1,
                        EnumSet.allOf(ReminderChannel.class))), ReminderSendingOverrides.none());
    }

    public static ReminderSchedule followingGroup() {
        return new ReminderSchedule(true, List.of(), ReminderSendingOverrides.none());
    }

    public List<ReminderNotice> orderedNotices() {
        return notices.stream().sorted(TIMELINE_ORDER).toList();
    }
}
