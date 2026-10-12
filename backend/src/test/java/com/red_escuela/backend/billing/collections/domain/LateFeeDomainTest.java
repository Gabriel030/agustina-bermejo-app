package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LateFeeDomainTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 11);
    private final DailySimpleLateFeeCalculator calculator = new DailySimpleLateFeeCalculator();
    private final LateFeeAccrualPlanner planner = new LateFeeAccrualPlanner();

    private NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    private BigDecimal lateFee(String price, String points, int days) {
        return calculator.calculateLateFee(ars(price), Percentage.ofPoints(new BigDecimal(points)), days).value().amount();
    }

    private static LateFeeAccrualPeriod period(String first, String last) {
        return new LateFeeAccrualPeriod(LocalDate.parse(first), LocalDate.parse(last));
    }

    private List<PlannedLateFeePeriod> plan(int grace, List<LateFeeAccrualPeriod> covered, LocalDate cutoffDay, Optional<LocalDate> frozenAfter) {
        return planWith(LateFeeCutoffKind.MONTH_CLOSE, grace, covered, cutoffDay, frozenAfter);
    }

    private List<PlannedLateFeePeriod> planWith(LateFeeCutoffKind kind, int grace, List<LateFeeAccrualPeriod> covered, LocalDate cutoffDay, Optional<LocalDate> frozenAfter) {
        return planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, grace), covered, new LateFeeCutoff(kind, cutoffDay), day -> true, day -> frozenAfter.map(day::isAfter).orElse(false));
    }

    @Test
    @Tag("D-099")
    @Tag("D-330")
    void dailyLateFeeRoundsHalfUpOncePerDebitNote() {
        assertThat(lateFee("100002.00", "0.25", 1)).isEqualByComparingTo("250.01");
        assertThat(lateFee("100001.00", "0.17", 20)).isEqualByComparingTo("3400.03");
        assertThat(lateFee("100000.00", "0.20", 0)).isEqualByComparingTo("0.00");
        assertThat(lateFee("100000.00", "0.20", 20)).isEqualByComparingTo("4000.00");
        assertThatThrownBy(() -> calculator.calculateLateFee(ars("100"), Percentage.ofPoints(BigDecimal.ONE), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-332")
    void accrualPeriodsAreSplitByCalendarMonth() {
        DailyLateFeeSchedule march = new DailyLateFeeSchedule(DUE, 0);
        assertThat(march.splitByCalendarMonth(LocalDate.of(2027, 3, 12), LocalDate.of(2027, 5, 9))).containsExactly(
                period("2027-03-12", "2027-03-31"), period("2027-04-01", "2027-04-30"), period("2027-05-01", "2027-05-09"));
        assertThat(march.splitByCalendarMonth(LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 12))).containsExactly(period("2027-03-12", "2027-03-12"));
        assertThat(march.splitByCalendarMonth(LocalDate.of(2027, 3, 12), LocalDate.of(2027, 3, 11))).isEmpty();
        DailyLateFeeSchedule leap = new DailyLateFeeSchedule(LocalDate.of(2028, 2, 20), 0);
        List<LateFeeAccrualPeriod> leapPeriods = leap.splitByCalendarMonth(LocalDate.of(2028, 2, 21), LocalDate.of(2028, 3, 2));
        assertThat(leapPeriods).containsExactly(period("2028-02-21", "2028-02-29"), period("2028-03-01", "2028-03-02"));
        assertThat(leapPeriods).extracting(LateFeeAccrualPeriod::days).containsExactly(9, 2);
        assertThatThrownBy(() -> period("2027-03-25", "2027-04-02")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> period("2027-03-26", "2027-03-25")).isInstanceOf(IllegalArgumentException.class);
        assertThat(period("2027-03-12", "2027-03-31").accrualMonth()).hasToString("2027-03");
        assertThat(march.firstChargeableDay()).isEqualTo(LocalDate.of(2027, 3, 12));
    }

    @Test
    @Tag("D-331")
    void graceIsToleranceAndDaysCountFromOriginalDueDate() {
        LateFeeCutoffKind payment = LateFeeCutoffKind.SETTLEMENT;
        assertThat(planWith(payment, 10, List.of(), LocalDate.of(2027, 3, 20), Optional.empty())).isEmpty();
        assertThat(planWith(payment, 10, List.of(), LocalDate.of(2027, 3, 21), Optional.empty())).isEmpty();
        assertThat(planWith(payment, 10, List.of(), LocalDate.of(2027, 3, 22), Optional.empty()))
                .containsExactly(new PlannedLateFeePeriod(period("2027-03-12", "2027-03-22"), 11, payment));
        assertThat(planWith(payment, 10, List.of(), LocalDate.of(2027, 3, 25), Optional.empty()).get(0).chargedDays()).isEqualTo(14);
        assertThat(plan(10, List.of(), LocalDate.of(2027, 3, 31), Optional.empty())).extracting(PlannedLateFeePeriod::chargedDays).containsExactly(20);
        DailyLateFeeSchedule schedule = new DailyLateFeeSchedule(DUE, 10);
        assertThat(schedule.isPastGraceOn(LocalDate.of(2027, 3, 21))).isFalse();
        assertThat(schedule.isPastGraceOn(LocalDate.of(2027, 3, 22))).isTrue();
    }

    @Test
    @Tag("D-332")
    void plannerContinuesFromTheDayAfterTheLastCoveredDayAndNeverRepeatsADay() {
        List<LateFeeAccrualPeriod> covered = List.of(period("2027-03-12", "2027-03-31"));
        assertThat(plan(0, covered, LocalDate.of(2027, 3, 31), Optional.empty())).isEmpty();
        assertThat(plan(0, covered, LocalDate.of(2027, 4, 30), Optional.empty())).extracting(PlannedLateFeePeriod::period).containsExactly(period("2027-04-01", "2027-04-30"));
        assertThat(plan(0, covered, LocalDate.of(2027, 5, 31), Optional.empty())).extracting(PlannedLateFeePeriod::period)
                .containsExactly(period("2027-04-01", "2027-04-30"), period("2027-05-01", "2027-05-31"));
        List<LateFeeAccrualPeriod> withReversal = List.of(period("2027-03-12", "2027-03-31"), period("2027-04-01", "2027-04-09"));
        assertThat(plan(0, withReversal, LocalDate.of(2027, 4, 30), Optional.empty())).extracting(PlannedLateFeePeriod::period).containsExactly(period("2027-04-10", "2027-04-30"));
        assertThat(plan(0, covered, LocalDate.of(2027, 3, 20), Optional.empty())).isEmpty();
    }

    @Test
    @Tag("D-293")
    void plannerCutsAtTheEndDayOfTheEnrollmentAndKeepsTheEndDay() {
        assertThat(plan(0, List.of(), LocalDate.of(2027, 6, 30), Optional.of(LocalDate.of(2027, 3, 20)))).extracting(PlannedLateFeePeriod::period, PlannedLateFeePeriod::chargedDays)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(period("2027-03-12", "2027-03-20"), 9));
        assertThat(plan(0, List.of(), LocalDate.of(2027, 6, 30), Optional.of(LocalDate.of(2027, 3, 12)))).extracting(PlannedLateFeePeriod::chargedDays).containsExactly(1);
        assertThat(plan(0, List.of(), LocalDate.of(2027, 6, 30), Optional.of(DUE))).isEmpty();
        assertThat(plan(0, List.of(), LocalDate.of(2027, 3, 31), Optional.of(LocalDate.of(2027, 8, 1)))).extracting(PlannedLateFeePeriod::chargedDays).containsExactly(20);
    }

    @Test
    @Tag("D-148")
    @Tag("D-360")
    @Tag("D-293")
    void everyDayAfterTheEndStaysFrozenWhenTheRuleOfTheEndDateFreezes() {
        LocalDate end = LocalDate.of(2027, 3, 20);
        List<PlannedLateFeePeriod> planned = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), List.of(),
                new LateFeeCutoff(LateFeeCutoffKind.MONTH_CLOSE, LocalDate.of(2027, 6, 30)), day -> true, day -> day.isAfter(end));
        assertThat(planned).extracting(PlannedLateFeePeriod::period, PlannedLateFeePeriod::chargedDays).containsExactly(
                org.assertj.core.groups.Tuple.tuple(period("2027-03-12", "2027-03-20"), 9));
    }

    @Test
    @Tag("D-294")
    void plannerChargesOnlyTheDaysWithChargingEnabledAndDropsPeriodsWithoutThem() {
        List<PlannedLateFeePeriod> fromTheTwentieth = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), List.of(),
                new LateFeeCutoff(LateFeeCutoffKind.MONTH_CLOSE, LocalDate.of(2027, 4, 30)), day -> !day.isBefore(LocalDate.of(2027, 3, 20)), day -> false);
        assertThat(fromTheTwentieth).extracting(PlannedLateFeePeriod::period, PlannedLateFeePeriod::chargedDays).containsExactly(
                org.assertj.core.groups.Tuple.tuple(period("2027-03-12", "2027-03-31"), 12), org.assertj.core.groups.Tuple.tuple(period("2027-04-01", "2027-04-30"), 30));
        List<PlannedLateFeePeriod> onlyJune = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), List.of(),
                new LateFeeCutoff(LateFeeCutoffKind.MONTH_CLOSE, LocalDate.of(2027, 6, 30)), day -> !day.isBefore(LocalDate.of(2027, 6, 1)), day -> false);
        assertThat(onlyJune).extracting(PlannedLateFeePeriod::period).containsExactly(period("2027-06-01", "2027-06-30"));
        assertThat(planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), List.of(), new LateFeeCutoff(LateFeeCutoffKind.MONTH_CLOSE, LocalDate.of(2027, 6, 30)),
                day -> false, day -> false)).isEmpty();
    }

    @Test
    @Tag("D-330")
    void plannedPeriodAndCutoffRejectInvalidValues() {
        assertThatThrownBy(() -> new PlannedLateFeePeriod(period("2027-03-12", "2027-03-14"), 4, LateFeeCutoffKind.MONTH_CLOSE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedLateFeePeriod(period("2027-03-12", "2027-03-14"), -1, LateFeeCutoffKind.MONTH_CLOSE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyLateFeeSchedule(DUE, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(LateFeeCutoff.closingOfMonthBefore(LocalDate.of(2027, 4, 1)).lastDay()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(LateFeeCutoff.closingOfMonthBefore(LocalDate.of(2027, 1, 20)).lastDay()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @Tag("D-332")
    void chargeKeyIsBuiltByOriginAndFirstDayOfThePeriod() {
        assertThat(ChargeKey.forLateFee(812L, LocalDate.of(2027, 3, 12)).value()).isEqualTo("LF:812:D2027-03-12");
        assertThat(ChargeKey.forLateFee(812L, LocalDate.of(2027, 4, 1)).value()).isEqualTo("LF:812:D2027-04-01");
    }
}
