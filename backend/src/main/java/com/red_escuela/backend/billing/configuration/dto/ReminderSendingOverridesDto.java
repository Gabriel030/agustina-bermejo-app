package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;

import java.time.LocalTime;

public record ReminderSendingOverridesDto(Boolean suspensionNoticeEnabled, Integer postWithdrawalSummaryMonths,
                                          LocalTime emailWindowStart, LocalTime emailWindowEnd, Integer dailyEmailCap,
                                          Integer contactListDaysOverdue, Integer contactListOverdueInstallments) {

    public ReminderSendingOverrides toOverrides() {
        return new ReminderSendingOverrides(suspensionNoticeEnabled, postWithdrawalSummaryMonths,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments);
    }

    public static ReminderSendingOverridesDto from(ReminderSendingOverrides overrides) {
        return new ReminderSendingOverridesDto(overrides.suspensionNoticeEnabled(), overrides.postWithdrawalSummaryMonths(),
                overrides.emailWindowStart(), overrides.emailWindowEnd(), overrides.dailyEmailCap(),
                overrides.contactListDaysOverdue(), overrides.contactListOverdueInstallments());
    }
}
