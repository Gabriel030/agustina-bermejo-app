package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;

import java.time.LocalTime;

public record ReminderSendingOverridesDto(Boolean suspensionNoticeEnabled, Integer postWithdrawalSummaryMonths, Integer minDaysBetweenEmails,
                                          Integer maxReminderEmailsPerMonth, LocalTime emailWindowStart, LocalTime emailWindowEnd, Integer dailyEmailCap,
                                          Integer contactListDaysOverdue, Integer contactListOverdueInstallments) {

    public ReminderSendingOverrides toOverrides() {
        return new ReminderSendingOverrides(suspensionNoticeEnabled, postWithdrawalSummaryMonths, minDaysBetweenEmails, maxReminderEmailsPerMonth,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments);
    }

    public static ReminderSendingOverridesDto from(ReminderSendingOverrides overrides) {
        return new ReminderSendingOverridesDto(overrides.suspensionNoticeEnabled(), overrides.postWithdrawalSummaryMonths(), overrides.minDaysBetweenEmails(),
                overrides.maxReminderEmailsPerMonth(), overrides.emailWindowStart(), overrides.emailWindowEnd(), overrides.dailyEmailCap(),
                overrides.contactListDaysOverdue(), overrides.contactListOverdueInstallments());
    }
}
