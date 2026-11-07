package com.red_escuela.backend.platform.api;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.domain.InstituteSettingState;
import com.red_escuela.backend.platform.domain.InstituteSettingsSnapshot;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMethodAvailabilityTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void methodIsEffectiveOnlyWhenBillingFullAndPlatformAndInstituteEnabled() {
        for (BillingMode mode : BillingMode.values()) {
            for (boolean platformEnabled : new boolean[]{true, false}) {
                for (boolean instituteEnabled : new boolean[]{true, false}) {
                    PaymentMethodAvailability availability = availabilityWith(mode, platformEnabled, instituteEnabled);
                    boolean expected = mode == BillingMode.FULL && platformEnabled && instituteEnabled;
                    for (PaymentMethodCode code : PaymentMethodCode.values()) {
                        assertThat(availability.isAvailable(code))
                                .as("mode=%s platform=%s institute=%s method=%s", mode, platformEnabled, instituteEnabled, code)
                                .isEqualTo(expected);
                    }
                }
            }
        }
    }

    @Test
    void statusOfAllReportsPlatformAndInstituteFlagsSeparately() {
        PaymentMethodAvailability availability = availabilityWith(BillingMode.FULL, false, true);

        assertThat(availability.statusOfAll()).hasSize(PaymentMethodCode.values().length)
                .allSatisfy(status -> {
                    assertThat(status.platformEnabled()).isFalse();
                    assertThat(status.instituteEnabled()).isTrue();
                    assertThat(status.effective()).isFalse();
                });
    }

    @Test
    void eachMethodReadsItsOwnPlatformAndInstituteFlags() {
        Map<FeatureCode, FeatureState> features = new HashMap<>();
        features.put(FeatureCode.BILLING, FeatureState.billing(BillingMode.FULL, 0));
        features.put(FeatureCode.PAYMENT_METHOD_CASH, new FeatureState(true, null, 0));
        features.put(FeatureCode.PAYMENT_METHOD_BANK_TRANSFER, new FeatureState(false, null, 0));
        features.put(FeatureCode.PAYMENT_METHOD_GATEWAY, new FeatureState(true, null, 0));
        Map<InstituteSettingCode, InstituteSettingState> settings = new HashMap<>();
        settings.put(InstituteSettingCode.PAYMENT_METHOD_CASH, new InstituteSettingState(true, 0));
        settings.put(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, new InstituteSettingState(true, 0));
        settings.put(InstituteSettingCode.PAYMENT_METHOD_GATEWAY, new InstituteSettingState(false, 0));
        PaymentMethodAvailability availability = availabilityOf(features, settings);

        assertThat(availability.isAvailable(PaymentMethodCode.CASH)).isTrue();
        assertThat(availability.isAvailable(PaymentMethodCode.BANK_TRANSFER)).isFalse();
        assertThat(availability.isAvailable(PaymentMethodCode.GATEWAY)).isFalse();
    }

    @Test
    void billingNotificationsAreEffectiveOnlyWhenBillingFullAndPlatformAndInstituteEnabled() {
        for (BillingMode mode : BillingMode.values()) {
            for (boolean platformEnabled : new boolean[]{true, false}) {
                for (boolean instituteEnabled : new boolean[]{true, false}) {
                    BillingNotificationsPolicy policy = policyWith(mode, platformEnabled, instituteEnabled);
                    assertThat(policy.areBillingNotificationsEffective())
                            .as("mode=%s platform=%s institute=%s", mode, platformEnabled, instituteEnabled)
                            .isEqualTo(mode == BillingMode.FULL && platformEnabled && instituteEnabled);
                }
            }
        }
    }

    private PaymentMethodAvailability availabilityWith(BillingMode mode, boolean platformEnabled, boolean instituteEnabled) {
        Map<FeatureCode, FeatureState> features = new HashMap<>();
        features.put(FeatureCode.BILLING, FeatureState.billing(mode, 0));
        Map<InstituteSettingCode, InstituteSettingState> settings = new HashMap<>();
        for (PaymentMethodCode code : PaymentMethodCode.values()) {
            features.put(code.featureCode(), new FeatureState(platformEnabled, null, 0));
            settings.put(code.settingCode(), new InstituteSettingState(instituteEnabled, 0));
        }
        return availabilityOf(features, settings);
    }

    private BillingNotificationsPolicy policyWith(BillingMode mode, boolean platformEnabled, boolean instituteEnabled) {
        Map<FeatureCode, FeatureState> features = new HashMap<>();
        features.put(FeatureCode.BILLING, FeatureState.billing(mode, 0));
        features.put(FeatureCode.BILLING_NOTIFICATIONS, new FeatureState(platformEnabled, null, 0));
        Map<InstituteSettingCode, InstituteSettingState> settings = new HashMap<>();
        settings.put(InstituteSettingCode.BILLING_NOTIFICATIONS, new InstituteSettingState(instituteEnabled, 0));
        return new BillingNotificationsPolicy(featureHolder(features), instituteHolder(settings));
    }

    private PaymentMethodAvailability availabilityOf(Map<FeatureCode, FeatureState> features, Map<InstituteSettingCode, InstituteSettingState> settings) {
        return new PaymentMethodAvailability(featureHolder(features), instituteHolder(settings));
    }

    private FeatureSnapshotHolder featureHolder(Map<FeatureCode, FeatureState> features) {
        FeatureSnapshotHolder holder = mock(FeatureSnapshotHolder.class);
        when(holder.current()).thenReturn(new FeatureSnapshot(features, NOW));
        return holder;
    }

    private InstituteSettingsSnapshotHolder instituteHolder(Map<InstituteSettingCode, InstituteSettingState> settings) {
        InstituteSettingsSnapshotHolder holder = mock(InstituteSettingsSnapshotHolder.class);
        when(holder.current()).thenReturn(new InstituteSettingsSnapshot(settings, NOW));
        return holder;
    }
}
