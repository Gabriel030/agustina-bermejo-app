package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalTime;

public record CollectionReminderSettings(boolean suspensionNoticeEnabled, int postWithdrawalSummaryMonths, LocalTime emailWindowStart, LocalTime emailWindowEnd, int dailyEmailCap,
                                         int contactListDaysOverdue, int contactListOverdueInstallments) implements ConfigurationValues {

    public CollectionReminderSettings {
        if (emailWindowStart == null || emailWindowEnd == null) {
            throw new InvalidConfigurationValuesException("La franja de envío de mails es obligatoria");
        }
    }

    public static CollectionReminderSettings initial() {
        return new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2);
    }

    public ReminderSendingOverrides asOverrides() {
        return new ReminderSendingOverrides(suspensionNoticeEnabled, postWithdrawalSummaryMonths,
                emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COLLECTION_REMINDERS;
    }
}
