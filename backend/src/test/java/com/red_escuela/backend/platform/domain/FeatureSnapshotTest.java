package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeatureSnapshotTest {

    private static final Instant LOADED_AT = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void missingBillingRowFailsClosedAsOff() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of(), LOADED_AT);

        assertThat(snapshot.billingMode()).isEqualTo(BillingMode.OFF);
        assertThat(snapshot.isEnabled(FeatureCode.PAYMENT_METHOD_CASH)).isFalse();
    }

    @Test
    void reportsTheStoredStateOfEachFeature() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of(
                FeatureCode.BILLING, FeatureState.billing(BillingMode.MIRROR, 3),
                FeatureCode.ANALYTICS, new FeatureState(true, null, 1)), LOADED_AT);

        assertThat(snapshot.billingMode()).isEqualTo(BillingMode.MIRROR);
        assertThat(snapshot.isEnabled(FeatureCode.BILLING)).isTrue();
        assertThat(snapshot.isEnabled(FeatureCode.ANALYTICS)).isTrue();
        assertThat(snapshot.isEnabled(FeatureCode.FISCAL_INVOICING)).isFalse();
    }

    @Test
    void isOlderThanIsTrueExactlyAtTheTimeToLive() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of(), LOADED_AT);
        Duration ttl = Duration.ofSeconds(30);

        assertThat(snapshot.isOlderThan(ttl, LOADED_AT.plusSeconds(29))).isFalse();
        assertThat(snapshot.isOlderThan(ttl, LOADED_AT.plusSeconds(30))).isTrue();
    }

    @Test
    void statesCannotBeModifiedAfterCreation() {
        FeatureSnapshot snapshot = new FeatureSnapshot(Map.of(FeatureCode.BILLING, FeatureState.billing(BillingMode.FULL, 0)), LOADED_AT);

        assertThatThrownBy(() -> snapshot.states().put(FeatureCode.ANALYTICS, FeatureState.disabled()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void billingStateEnablesOnlyWhenTheModeIsNotOff() {
        assertThat(FeatureState.billing(BillingMode.OFF, 0).enabled()).isFalse();
        assertThat(FeatureState.billing(BillingMode.MIRROR, 0).enabled()).isTrue();
        assertThat(FeatureState.billing(BillingMode.FULL, 0).enabled()).isTrue();
    }

    @Test
    void billingModesAreOrderedOffMirrorFull() {
        assertThat(BillingMode.FULL.isAtLeast(BillingMode.MIRROR)).isTrue();
        assertThat(BillingMode.MIRROR.isAtLeast(BillingMode.FULL)).isFalse();
        assertThat(BillingMode.OFF.isAtLeast(BillingMode.OFF)).isTrue();
        assertThat(BillingMode.OFF.isAtLeast(BillingMode.MIRROR)).isFalse();
    }
}
