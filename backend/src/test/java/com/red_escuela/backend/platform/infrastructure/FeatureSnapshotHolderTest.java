package com.red_escuela.backend.platform.infrastructure;

import com.red_escuela.backend.platform.domain.FeatureSnapshot;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureSnapshotHolderTest {

    private static final Instant START = Instant.parse("2026-11-02T12:00:00Z");

    private final FeatureSnapshotLoader loader = mock(FeatureSnapshotLoader.class);
    private final MutableClock clock = MutableClock.INSTANCE;
    private FeatureSnapshotHolder holder;

    @BeforeEach
    void startAtAFixedInstant() {
        clock.setInstant(START);
        holder = new FeatureSnapshotHolder(loader, clock);
    }

    @AfterEach
    void releaseTheSharedClock() {
        clock.reset();
    }

    @Test
    void firstReadLoadsTheSnapshotAndLaterReadsWithinTheTimeToLiveReuseIt() {
        when(loader.loadCommittedSnapshot()).thenReturn(snapshotWith(BillingMode.FULL));

        holder.current();
        clock.advance(Duration.ofSeconds(29));
        FeatureSnapshot second = holder.current();

        verify(loader, times(1)).loadCommittedSnapshot();
        assertThat(second.billingMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void snapshotIsReloadedOnceTheTimeToLiveElapses() {
        when(loader.loadCommittedSnapshot()).thenReturn(snapshotWith(BillingMode.FULL), snapshotWith(BillingMode.OFF));

        holder.current();
        clock.advance(FeatureSnapshotHolder.TIME_TO_LIVE);
        FeatureSnapshot reloaded = holder.current();

        verify(loader, times(2)).loadCommittedSnapshot();
        assertThat(reloaded.billingMode()).isEqualTo(BillingMode.OFF);
    }

    @Test
    void explicitRefreshReplacesTheSnapshotImmediately() {
        when(loader.loadCommittedSnapshot()).thenReturn(snapshotWith(BillingMode.FULL), snapshotWith(BillingMode.MIRROR));

        holder.current();
        holder.refresh();

        assertThat(holder.current().billingMode()).isEqualTo(BillingMode.MIRROR);
    }

    @Test
    void failedRefreshKeepsTheLastKnownSnapshot() {
        when(loader.loadCommittedSnapshot()).thenReturn(snapshotWith(BillingMode.FULL)).thenThrow(new IllegalStateException("db down"));

        holder.current();
        clock.advance(Duration.ofMinutes(5));

        assertThat(holder.current().billingMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void failedFirstLoadPropagatesInsteadOfInventingAMode() {
        when(loader.loadCommittedSnapshot()).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> holder.current()).isInstanceOf(IllegalStateException.class);
    }

    private FeatureSnapshot snapshotWith(BillingMode mode) {
        return new FeatureSnapshot(Map.of(FeatureCode.BILLING, FeatureState.billing(mode, 0)), clock.instant());
    }
}
