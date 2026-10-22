package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.billing.configuration.dto.CollectionReminderSettingsValues;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderDailyDigestSettingTest {

    private static CollectionReminderSettings withDailyDigest(boolean enabled) {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();
        return new CollectionReminderSettings(initial.suspensionNoticeEnabled(), initial.postWithdrawalSummaryMonths(), initial.emailWindowStart(), initial.emailWindowEnd(),
                initial.dailyEmailCap(), initial.contactListDaysOverdue(), initial.contactListOverdueInstallments(), enabled);
    }

    @Test
    @Tag("D-411")
    void theDailyDigestIsEnabledByDefault() {
        assertThat(CollectionReminderSettings.initial().dailyDigestEnabled()).isTrue();
        assertThat(new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2).dailyDigestEnabled()).isTrue();
    }

    @Test
    @Tag("D-411")
    void settingsRoundTripKeepsTheDailyDigestFlag() {
        for (boolean enabled : new boolean[]{true, false}) {
            CollectionReminderSettings sample = withDailyDigest(enabled);

            ConfigurationValues restored = ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(sample));

            assertThat(restored).isEqualTo(sample);
            assertThat(ConfigurationValuesCodec.toJson(sample)).contains("\"dailyDigestEnabled\":" + enabled);
        }
    }

    @Test
    @Tag("D-411")
    void aStoredVersionWithoutTheFlagReadsAsEnabledAndAnExplicitFalseIsKept() {
        String legacy = "{\"type\":\"COLLECTION_REMINDERS\",\"suspensionNoticeEnabled\":true,\"postWithdrawalSummaryMonths\":6,\"emailWindowStart\":\"09:00:00\","
                + "\"emailWindowEnd\":\"11:00:00\",\"dailyEmailCap\":250,\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2";

        assertThat(((CollectionReminderSettings) ConfigurationValuesCodec.fromJson(legacy + "}")).dailyDigestEnabled()).isTrue();
        assertThat(((CollectionReminderSettings) ConfigurationValuesCodec.fromJson(legacy + ",\"dailyDigestEnabled\":false}")).dailyDigestEnabled()).isFalse();
    }

    @Test
    @Tag("D-411")
    void aRequestWithoutTheFlagKeepsTheDigestEnabledAndAnExplicitFalseIsRespected() {
        CollectionReminderSettingsValues withoutFlag = CollectionReminderSettingsValues.from(withDailyDigest(true));
        CollectionReminderSettingsValues legacyClient = new CollectionReminderSettingsValues(withoutFlag.suspensionNoticeEnabled(), withoutFlag.postWithdrawalSummaryMonths(),
                withoutFlag.emailWindowStart(), withoutFlag.emailWindowEnd(), withoutFlag.dailyEmailCap(), withoutFlag.contactListDaysOverdue(),
                withoutFlag.contactListOverdueInstallments(), null);
        CollectionReminderSettingsValues explicitlyOff = CollectionReminderSettingsValues.from(withDailyDigest(false));

        assertThat(legacyClient.toSettings().dailyDigestEnabled()).isTrue();
        assertThat(explicitlyOff.dailyDigestEnabled()).isFalse();
        assertThat(explicitlyOff.toSettings().dailyDigestEnabled()).isFalse();
    }

    @Test
    @Tag("D-411")
    void overridesOfAGroupOrAnExceptionNeverChangeTheGlobalDigestFlag() {
        CollectionReminderSettings withoutDigest = withDailyDigest(false);

        CollectionReminderSettings overlaid = new ReminderSendingOverrides(false, 12, LocalTime.of(10, 0), LocalTime.of(12, 0), 100, 3, 4).overlayOn(withoutDigest);

        assertThat(overlaid.dailyDigestEnabled()).isFalse();
        assertThat(overlaid.dailyEmailCap()).isEqualTo(100);
        assertThat(withoutDigest.asOverrides()).isEqualTo(withDailyDigest(true).asOverrides());
    }
}
