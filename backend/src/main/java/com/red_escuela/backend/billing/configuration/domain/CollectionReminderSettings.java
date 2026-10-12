package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalTime;

public record CollectionReminderSettings(boolean emissionReminderEnabled, int daysAfterIssueReminder, int daysBeforeDueReminder,
                                         int daysAfterDueReminder, int daysBeforeSuspensionReminder, boolean suspensionNoticeEnabled,
                                         int postWithdrawalSummaryMonths, int minDaysBetweenEmails, int maxReminderEmailsPerMonth,
                                         LocalTime emailWindowStart, LocalTime emailWindowEnd, int dailyEmailCap,
                                         int contactListDaysOverdue, int contactListOverdueInstallments) implements ConfigurationValues {

    public CollectionReminderSettings {
        if (emailWindowStart == null || emailWindowEnd == null) {
            throw new InvalidConfigurationValuesException("La franja de envío de mails es obligatoria");
        }
    }

    public static CollectionReminderSettings initial() {
        return new CollectionReminderSettings(true, 5, 1, 10, 1, true, 6, 3, 4, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COLLECTION_REMINDERS;
    }
}
