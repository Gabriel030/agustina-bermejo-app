package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LateFeeSettlementPlanningTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 11);
    private final LateFeeAccrualPlanner planner = new LateFeeAccrualPlanner();

    private static LocalDate day(String iso) {
        return LocalDate.parse(iso);
    }

    private static LateFeeAccrualPeriod period(String first, String last) {
        return new LateFeeAccrualPeriod(day(first), day(last));
    }

    private List<PlannedLateFeePeriod> settle(int grace, List<LateFeeAccrualPeriod> covered, String paidOn) {
        return planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, grace), covered, LateFeeCutoff.settledOn(day(paidOn)), paid -> true, frozen -> false);
    }

    private static List<String> summary(List<PlannedLateFeePeriod> planned) {
        return planned.stream().map(p -> p.period().firstDay() + ".." + p.period().lastDay() + ":" + p.chargedDays() + ":" + p.kind()).toList();
    }

    @Test
    @Tag("D-331")
    void paymentOnTheDueDateOrOnTheLastGraceDayPlansNothingAndTheNextDayPlansEveryDay() {
        assertThat(settle(0, List.of(), "2027-03-11")).isEmpty();
        assertThat(settle(10, List.of(), "2027-03-21")).isEmpty();
        assertThat(summary(settle(10, List.of(), "2027-03-22"))).containsExactly("2027-03-12..2027-03-22:11:SETTLEMENT");
        assertThat(summary(settle(0, List.of(), "2027-03-12"))).containsExactly("2027-03-12..2027-03-12:1:SETTLEMENT");
    }

    @Test
    @Tag("D-330")
    @Tag("D-332")
    void paymentInTheSameMonthPlansTheDaysUpToThePaymentDateInclusive() {
        assertThat(summary(settle(0, List.of(), "2027-03-20"))).containsExactly("2027-03-12..2027-03-20:9:SETTLEMENT");
        assertThat(summary(settle(0, List.of(), "2027-03-25"))).containsExactly("2027-03-12..2027-03-25:14:SETTLEMENT");
        assertThat(summary(settle(10, List.of(), "2027-03-25"))).containsExactly("2027-03-12..2027-03-25:14:SETTLEMENT");
    }

    @Test
    @Tag("D-332")
    void paymentOnTheLastDayOfTheMonthPlansTheWholeRemainderOfThatMonth() {
        assertThat(summary(settle(0, List.of(), "2027-03-31"))).containsExactly("2027-03-12..2027-03-31:20:SETTLEMENT");
    }

    @Test
    @Tag("D-332")
    void paymentAfterTheCloseOfTheMonthPlansOnlyTheDaysNotCovered() {
        List<LateFeeAccrualPeriod> covered = List.of(period("2027-03-12", "2027-03-31"));

        assertThat(summary(settle(0, covered, "2027-04-09"))).containsExactly("2027-04-01..2027-04-09:9:SETTLEMENT");
        assertThat(summary(settle(0, covered, "2027-04-01"))).containsExactly("2027-04-01..2027-04-01:1:SETTLEMENT");
    }

    @Test
    @Tag("D-332")
    @Tag("D-325")
    void paymentBeforeAMissedCloseCoversTheUnclosedMonthsSplitByCalendarMonth() {
        assertThat(summary(settle(0, List.of(), "2027-04-03"))).containsExactly("2027-03-12..2027-03-31:20:SETTLEMENT", "2027-04-01..2027-04-03:3:SETTLEMENT");
    }

    @Test
    @Tag("D-308")
    @Tag("D-332")
    void paymentDatedBeforeDaysAlreadyCoveredPlansNothing() {
        List<LateFeeAccrualPeriod> covered = List.of(period("2027-03-12", "2027-03-31"));

        assertThat(settle(0, covered, "2027-03-28")).isEmpty();
        assertThat(settle(0, covered, "2027-03-31")).isEmpty();
    }

    @Test
    @Tag("D-308")
    @Tag("D-332")
    void paymentDatedBeforeAMissedCloseStopsAtTheMoneyDate() {
        assertThat(summary(settle(0, List.of(), "2027-03-28"))).containsExactly("2027-03-12..2027-03-28:17:SETTLEMENT");
    }

    @Test
    @Tag("D-332")
    @Tag("D-290")
    void afterAReversalTheNextPlanResumesFromTheDayAfterTheLastCoveredDay() {
        List<LateFeeAccrualPeriod> covered = List.of(period("2027-03-12", "2027-03-31"), period("2027-04-01", "2027-04-09"));

        List<PlannedLateFeePeriod> close = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), covered, LateFeeCutoff.closingOfMonthBefore(day("2027-05-01")),
                paid -> true, frozen -> false);
        assertThat(summary(close)).containsExactly("2027-04-10..2027-04-30:21:MONTH_CLOSE");

        List<LateFeeAccrualPeriod> afterClose = List.of(covered.get(0), covered.get(1), close.get(0).period());
        assertThat(summary(settle(0, afterClose, "2027-05-10"))).containsExactly("2027-05-01..2027-05-10:10:SETTLEMENT");
    }

    @Test
    @Tag("D-293")
    void aFrozenEnrollmentCutsTheFinalPeriodAtTheEndDate() {
        List<LateFeeAccrualPeriod> covered = List.of(period("2027-03-12", "2027-03-31"));

        List<PlannedLateFeePeriod> planned = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), covered, LateFeeCutoff.settledOn(day("2027-04-30")),
                paid -> true, frozenDay -> frozenDay.isAfter(day("2027-04-20")));

        assertThat(summary(planned)).containsExactly("2027-04-01..2027-04-20:20:SETTLEMENT");
    }

    @Test
    @Tag("D-294")
    void settlementCountsOnlyTheEnabledDays() {
        List<PlannedLateFeePeriod> planned = planner.planMissingPeriods(new DailyLateFeeSchedule(DUE, 0), List.of(), LateFeeCutoff.settledOn(day("2027-03-20")),
                enabledDay -> !enabledDay.isBefore(day("2027-03-16")), frozen -> false);

        assertThat(summary(planned)).containsExactly("2027-03-12..2027-03-20:5:SETTLEMENT");
    }

    @Test
    void theSettlementCutoffCarriesItsKindAndTheDayOfThePayment() {
        LateFeeCutoff cutoff = LateFeeCutoff.settledOn(day("2027-03-20"));

        assertThat(cutoff.kind()).isEqualTo(LateFeeCutoffKind.SETTLEMENT);
        assertThat(cutoff.lastDay()).isEqualTo(day("2027-03-20"));
    }

    @Test
    @Tag("D-332")
    void daysAfterCountsOnlyTheDaysOfThePeriodPastTheGivenDay() {
        LateFeeAccrualPeriod march = period("2027-03-12", "2027-03-31");

        assertThat(march.daysAfter(day("2027-03-28"))).isEqualTo(3);
        assertThat(march.daysAfter(day("2027-03-30"))).isEqualTo(1);
        assertThat(march.daysAfter(day("2027-03-31"))).isZero();
        assertThat(march.daysAfter(day("2027-04-02"))).isZero();
        assertThat(march.daysAfter(day("2027-03-12"))).isEqualTo(19);
        assertThat(march.daysAfter(day("2027-03-11"))).isEqualTo(20);
        assertThat(march.daysAfter(day("2027-02-01"))).isEqualTo(20);
    }
}
