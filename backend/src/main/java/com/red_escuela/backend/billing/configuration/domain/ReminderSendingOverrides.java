package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalTime;

public record ReminderSendingOverrides(Boolean suspensionNoticeEnabled, Integer postWithdrawalSummaryMonths,
                                       LocalTime emailWindowStart, LocalTime emailWindowEnd,
                                       Integer dailyEmailCap, Integer contactListDaysOverdue, Integer contactListOverdueInstallments) {

    public static ReminderSendingOverrides none() {
        return new ReminderSendingOverrides(null, null, null, null, null, null, null);
    }

    public CollectionReminderSettings overlayOn(CollectionReminderSettings base) {
        return new CollectionReminderSettings(
                suspensionNoticeEnabled != null ? suspensionNoticeEnabled : base.suspensionNoticeEnabled(),
                postWithdrawalSummaryMonths != null ? postWithdrawalSummaryMonths : base.postWithdrawalSummaryMonths(),
                emailWindowStart != null ? emailWindowStart : base.emailWindowStart(),
                emailWindowEnd != null ? emailWindowEnd : base.emailWindowEnd(),
                dailyEmailCap != null ? dailyEmailCap : base.dailyEmailCap(),
                contactListDaysOverdue != null ? contactListDaysOverdue : base.contactListDaysOverdue(),
                contactListOverdueInstallments != null ? contactListOverdueInstallments : base.contactListOverdueInstallments());
    }
}
