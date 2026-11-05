package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-375")
class LateFeeCorrectionDomainTest {
    private static final LateFeeAccrualPeriod RANGE = new LateFeeAccrualPeriod(LocalDate.of(2027, 4, 29), LocalDate.of(2027, 4, 30));
    private static final NonNegativeMoney CREDIT = NonNegativeMoney.of(new BigDecimal("400.00"), Currency.ARS);

    @Test
    void aFreezeOnlyFreezesTheDaysAfterTheEndAndOnlyWhenTheRuleOfTheEndDateFreezes() {
        LateFeeFreeze frozen = new LateFeeFreeze(Optional.of(LocalDate.of(2027, 4, 28)), true);

        assertThat(frozen.frozenOn().test(LocalDate.of(2027, 4, 29))).isTrue();
        assertThat(frozen.frozenOn().test(LocalDate.of(2027, 4, 28))).isFalse();
        assertThat(frozen.frozenOn().test(LocalDate.of(2027, 4, 27))).isFalse();
        assertThat(new LateFeeFreeze(Optional.of(LocalDate.of(2027, 4, 28)), false).frozenOn().test(LocalDate.of(2027, 4, 29))).isFalse();
        assertThat(new LateFeeFreeze(Optional.empty(), true).frozenOn().test(LocalDate.of(2027, 4, 29))).isFalse();
    }

    @Test
    void aChargeSnapshotNeedsAtLeastOneChargedDayAndNoMoreThanTheDaysOfItsPeriod() {
        LateFeeAccrualPeriod period = new LateFeeAccrualPeriod(LocalDate.of(2027, 4, 12), LocalDate.of(2027, 4, 30));
        Percentage rate = Percentage.ofFraction(new BigDecimal("0.002"));

        assertThat(new LateFeeChargeSnapshot(1L, 2L, period, 1, CREDIT, rate).chargedDays()).isEqualTo(1);
        assertThat(new LateFeeChargeSnapshot(1L, 2L, period, 19, CREDIT, rate).chargedDays()).isEqualTo(19);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, 2L, period, 0, CREDIT, rate)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, 2L, period, 20, CREDIT, rate)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(null, 2L, period, 1, CREDIT, rate)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, null, period, 1, CREDIT, rate)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, 2L, null, 1, CREDIT, rate)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, 2L, period, 1, null, rate)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LateFeeChargeSnapshot(1L, 2L, period, 1, CREDIT, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void anAppliedOrPlannedCorrectionNeedsBetweenOneDayAndTheDaysOfItsRange() {
        assertThat(new AppliedLateFeeCorrection(RANGE, 1).correctedDays()).isEqualTo(1);
        assertThat(new AppliedLateFeeCorrection(RANGE, 2).correctedDays()).isEqualTo(2);
        assertThatThrownBy(() -> new AppliedLateFeeCorrection(RANGE, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AppliedLateFeeCorrection(RANGE, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PlannedLateFeeCorrection(RANGE, 1, CREDIT).correctedDays()).isEqualTo(1);
        assertThat(new PlannedLateFeeCorrection(RANGE, 2, CREDIT).correctedDays()).isEqualTo(2);
        assertThatThrownBy(() -> new PlannedLateFeeCorrection(RANGE, 0, CREDIT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedLateFeeCorrection(RANGE, 3, CREDIT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRecordedCorrectionKeepsWhatWasPlannedAndRejectsDaysThatAreNotAfterTheEnd() {
        PlannedLateFeeCorrection planned = new PlannedLateFeeCorrection(RANGE, 2, CREDIT);
        LocalDate end = LocalDate.of(2027, 4, 28);

        LateFeeChargeCorrection correction = LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(5L, 6L, 7L, 8L, planned, end);

        assertThat(correction.getLateFeeChargeId()).isEqualTo(5L);
        assertThat(correction.getDebitNoteId()).isEqualTo(6L);
        assertThat(correction.getStudentCurrentAccountId()).isEqualTo(7L);
        assertThat(correction.getCreditNoteId()).isEqualTo(8L);
        assertThat(correction.getFirstCorrectedDay()).isEqualTo(LocalDate.of(2027, 4, 29));
        assertThat(correction.getLastCorrectedDay()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(correction.getCorrectedDays()).isEqualTo(2);
        assertThat(correction.getCause()).isEqualTo(LateFeeCorrectionCause.BACKDATED_WITHDRAWAL);
        assertThat(correction.getEnrollmentEndDate()).isEqualTo(end);
        assertThat(correction.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(5L, 6L, 7L, 8L, planned, LocalDate.of(2027, 4, 29)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(null, 6L, 7L, 8L, planned, end)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(5L, null, 7L, 8L, planned, end)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(5L, 6L, null, 8L, planned, end)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(5L, 6L, 7L, null, planned, end)).isInstanceOf(NullPointerException.class);
    }
}
