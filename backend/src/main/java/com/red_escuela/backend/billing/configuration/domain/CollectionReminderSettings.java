package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;

public record CollectionReminderSettings(boolean suspensionNoticeEnabled, int postWithdrawalSummaryMonths, LocalTime emailWindowStart, LocalTime emailWindowEnd, int dailyEmailCap,
                                         int contactListDaysOverdue, int contactListOverdueInstallments, boolean dailyDigestEnabled) implements ConfigurationValues {

    public CollectionReminderSettings {
        if (emailWindowStart == null || emailWindowEnd == null) {
            throw new InvalidConfigurationValuesException("La franja de envío de mails es obligatoria");
        }
    }

    public CollectionReminderSettings(boolean suspensionNoticeEnabled, int postWithdrawalSummaryMonths, LocalTime emailWindowStart, LocalTime emailWindowEnd, int dailyEmailCap,
                                      int contactListDaysOverdue, int contactListOverdueInstallments) {
        this(suspensionNoticeEnabled, postWithdrawalSummaryMonths, emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue, contactListOverdueInstallments, true);
    }

    @JsonCreator
    public static CollectionReminderSettings fromJson(@JsonProperty("suspensionNoticeEnabled") boolean suspensionNoticeEnabled,
                                                      @JsonProperty("postWithdrawalSummaryMonths") int postWithdrawalSummaryMonths,
                                                      @JsonProperty("emailWindowStart") LocalTime emailWindowStart, @JsonProperty("emailWindowEnd") LocalTime emailWindowEnd,
                                                      @JsonProperty("dailyEmailCap") int dailyEmailCap, @JsonProperty("contactListDaysOverdue") int contactListDaysOverdue,
                                                      @JsonProperty("contactListOverdueInstallments") int contactListOverdueInstallments,
                                                      @JsonProperty("dailyDigestEnabled") Boolean dailyDigestEnabled) {
        return new CollectionReminderSettings(suspensionNoticeEnabled, postWithdrawalSummaryMonths, emailWindowStart, emailWindowEnd, dailyEmailCap, contactListDaysOverdue,
                contactListOverdueInstallments, dailyDigestEnabled == null || dailyDigestEnabled);
    }

    public static CollectionReminderSettings initial() {
        return new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2, true);
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
