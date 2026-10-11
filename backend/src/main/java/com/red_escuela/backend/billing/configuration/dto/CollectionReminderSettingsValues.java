package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CollectionReminderSettingsValues(@NotNull Boolean emissionReminderEnabled, @NotNull Integer daysAfterIssueReminder,
                                               @NotNull Integer daysBeforeDueReminder, @NotNull Integer daysAfterDueReminder,
                                               @NotNull Integer daysBeforeSuspensionReminder, @NotNull Boolean suspensionNoticeEnabled,
                                               @NotNull Integer postWithdrawalSummaryMonths, @NotNull Integer minDaysBetweenEmails,
                                               @NotNull Integer maxReminderEmailsPerMonth, @NotNull LocalTime emailWindowStart,
                                               @NotNull LocalTime emailWindowEnd, @NotNull Integer dailyEmailCap,
                                               @NotNull Integer contactListDaysOverdue, @NotNull Integer contactListOverdueInstallments) {

    public CollectionReminderSettings toSettings() {
        return new CollectionReminderSettings(emissionReminderEnabled, daysAfterIssueReminder, daysBeforeDueReminder, daysAfterDueReminder,
                daysBeforeSuspensionReminder, suspensionNoticeEnabled, postWithdrawalSummaryMonths, minDaysBetweenEmails, maxReminderEmailsPerMonth,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments);
    }

    public static CollectionReminderSettingsValues from(CollectionReminderSettings settings) {
        return new CollectionReminderSettingsValues(settings.emissionReminderEnabled(), settings.daysAfterIssueReminder(), settings.daysBeforeDueReminder(),
                settings.daysAfterDueReminder(), settings.daysBeforeSuspensionReminder(), settings.suspensionNoticeEnabled(),
                settings.postWithdrawalSummaryMonths(), settings.minDaysBetweenEmails(), settings.maxReminderEmailsPerMonth(), settings.emailWindowStart(),
                settings.emailWindowEnd(), settings.dailyEmailCap(), settings.contactListDaysOverdue(), settings.contactListOverdueInstallments());
    }
}
