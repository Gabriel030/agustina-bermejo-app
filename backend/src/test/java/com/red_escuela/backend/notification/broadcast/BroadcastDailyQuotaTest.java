package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastDailyQuota;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BroadcastDailyQuotaTest {

    private final BroadcastDailyQuota quota = new BroadcastDailyQuota(300, 100);

    @Test
    void withNoTrafficTheBroadcastsGetTheLimitMinusTheReserve() {
        assertThat(quota.remaining(0, 0)).isEqualTo(200);
    }

    @Test
    void transactionalBelowTheReserveStillHoldsTheWholeReserve() {
        assertThat(quota.remaining(99, 0)).isEqualTo(200);
        assertThat(quota.remaining(100, 0)).isEqualTo(200);
    }

    @Test
    void transactionalAboveTheReserveTakesTheDifferenceFromTheBroadcasts() {
        assertThat(quota.remaining(101, 0)).isEqualTo(199);
        assertThat(quota.remaining(150, 0)).isEqualTo(150);
    }

    @Test
    void sentBroadcastsAreDiscounted() {
        assertThat(quota.remaining(0, 120)).isEqualTo(80);
        assertThat(quota.remaining(150, 150)).isZero();
    }

    @Test
    void theRemainingQuotaIsNeverNegative() {
        assertThat(quota.remaining(400, 0)).isZero();
        assertThat(quota.remaining(0, 500)).isZero();
        assertThat(new BroadcastDailyQuota(50, 100).remaining(0, 0)).isZero();
    }

    @Test
    void negativeValuesAreRejected() {
        assertThatThrownBy(() -> new BroadcastDailyQuota(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BroadcastDailyQuota(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theQuotaIsExactlyZeroAtTheLimitAndOneJustBelow() {
        assertThat(quota.remaining(0, 199)).isEqualTo(1);
        assertThat(quota.remaining(0, 200)).isEqualTo(0);
        assertThat(quota.remaining(0, 201)).isEqualTo(0);
        assertThat(quota.remaining(101, 198)).isEqualTo(1);
        assertThat(quota.remaining(101, 199)).isEqualTo(0);
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Test
    void partialQuotaSpreadsFiveHundredPendingOverThreeDays() {
        assertThat(quota.completionDate(500, TODAY, 0, 0)).isEqualTo(TODAY.plusDays(2));
    }

    @Test
    void completionDateCountsWhatWasAlreadySentToday() {
        assertThat(quota.completionDate(500, TODAY, 150, 50)).isEqualTo(TODAY.plusDays(2));
        assertThat(quota.completionDate(300, TODAY, 150, 50)).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void completionDateIsTodayWhenTheRemainingQuotaCoversThePending() {
        assertThat(quota.completionDate(200, TODAY, 0, 0)).isEqualTo(TODAY);
        assertThat(quota.completionDate(201, TODAY, 0, 0)).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void completionDateIsNullWithoutPending() {
        assertThat(quota.completionDate(0, TODAY, 0, 0)).isNull();
    }

    @Test
    void completionDateIsNullWhenTheQuotaNeverAllowsBroadcasts() {
        assertThat(new BroadcastDailyQuota(100, 100).completionDate(10, TODAY, 100, 0)).isNull();
    }
}
