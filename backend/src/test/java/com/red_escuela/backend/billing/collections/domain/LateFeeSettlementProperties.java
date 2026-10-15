package com.red_escuela.backend.billing.collections.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-330")
@Tag("D-331")
@Tag("D-332")
@Tag("D-290")
@Tag("D-140")
class LateFeeSettlementProperties {
    private static final LocalDate START = LocalDate.of(2027, 1, 1);
    private static final LateFeeAccrualPlanner PLANNER = new LateFeeAccrualPlanner();

    enum Kind { ADVANCE, PAY, REVERSE }

    record Step(Kind kind, int value) {
    }

    record Scenario(int dueOffsetDays, int graceDays, int enabledFromOffsetDays, Optional<Integer> endOffsetDays, List<Step> steps) {
    }

    record Outcome(LocalDate dueDate, LocalDate lastDay, boolean paid, LocalDate paidOn, List<LateFeeAccrualPeriod> covered, List<LocalDate> issuedOn, List<LocalDate> reversalDates,
                   Set<LocalDate> oracleChargedDays, int overchargedDaysReported, int overchargedDaysExpected) {
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Step> step = Combinators.combine(Arbitraries.of(Kind.values()), Arbitraries.integers().between(0, 45)).as(Step::new);
        return Combinators.combine(Arbitraries.integers().between(0, 120), Arbitraries.integers().between(0, 15), Arbitraries.integers().between(0, 200),
                Arbitraries.integers().between(0, 220).optional(), step.list().ofMinSize(1).ofMaxSize(30)).as(Scenario::new);
    }

    private static Outcome simulate(Scenario scenario) {
        LocalDate due = START.plusDays(scenario.dueOffsetDays());
        DailyLateFeeSchedule schedule = new DailyLateFeeSchedule(due, scenario.graceDays());
        LocalDate enabledFrom = START.plusDays(scenario.enabledFromOffsetDays());
        Optional<LocalDate> end = scenario.endOffsetDays().map(START::plusDays);
        Predicate<LocalDate> enabledOn = day -> !day.isBefore(enabledFrom);
        Predicate<LocalDate> frozenOn = day -> end.map(day::isAfter).orElse(false);
        List<LateFeeAccrualPeriod> covered = new ArrayList<>();
        List<LocalDate> issuedOn = new ArrayList<>();
        List<LocalDate> reversalDates = new ArrayList<>();
        Set<LocalDate> oracle = new HashSet<>();
        LocalDate today = due.minusDays(15);
        boolean paid = false;
        LocalDate paidOn = null;
        int overchargedReported = 0;
        int overchargedExpected = 0;
        for (Step step : scenario.steps()) {
            switch (step.kind()) {
                case ADVANCE -> {
                    for (int i = 0; i < step.value(); i++) {
                        today = today.plusDays(1);
                        if (!paid) {
                            planClose(schedule, covered, issuedOn, today, enabledOn, frozenOn);
                            oracleClose(oracle, due, scenario.graceDays(), today, enabledOn, frozenOn);
                        }
                    }
                }
                case PAY -> {
                    if (!paid) {
                        paid = true;
                        paidOn = today.minusDays(step.value());
                        planSettlement(schedule, covered, issuedOn, paidOn, today, enabledOn, frozenOn);
                        oracleSettlement(oracle, due, scenario.graceDays(), paidOn, enabledOn, frozenOn);
                        LocalDate settledOn = paidOn;
                        Predicate<LocalDate> chargedOn = day -> enabledOn.test(day) && !frozenOn.test(day);
                        overchargedReported = covered.stream().mapToInt(period -> period.chargedDaysAfter(settledOn, chargedOn)).sum();
                        overchargedExpected = (int) covered.stream().flatMap(period -> period.firstDay().datesUntil(period.lastDay().plusDays(1))).filter(day -> day.isAfter(settledOn) && enabledOn.test(day) && !frozenOn.test(day)).count();
                    }
                }
                case REVERSE -> {
                    if (paid) {
                        paid = false;
                        paidOn = null;
                        reversalDates.add(today);
                        planClose(schedule, covered, issuedOn, today, enabledOn, frozenOn);
                        oracleClose(oracle, due, scenario.graceDays(), today, enabledOn, frozenOn);
                    }
                }
            }
        }
        return new Outcome(due, today, paid, paidOn, covered, issuedOn, reversalDates, oracle, overchargedReported, overchargedExpected);
    }

    private static void planClose(DailyLateFeeSchedule schedule, List<LateFeeAccrualPeriod> covered, List<LocalDate> issuedOn, LocalDate today, Predicate<LocalDate> enabledOn,
                                  Predicate<LocalDate> frozenOn) {
        if (!schedule.isPastGraceOn(today)) {
            return;
        }
        PLANNER.planMissingPeriods(schedule, covered, LateFeeCutoff.closingOfMonthBefore(today), enabledOn, frozenOn).forEach(planned -> {
            covered.add(planned.period());
            issuedOn.add(today);
        });
    }

    private static void planSettlement(DailyLateFeeSchedule schedule, List<LateFeeAccrualPeriod> covered, List<LocalDate> issuedOn, LocalDate paidOn, LocalDate today,
                                       Predicate<LocalDate> enabledOn, Predicate<LocalDate> frozenOn) {
        PLANNER.planMissingPeriods(schedule, covered, LateFeeCutoff.settledOn(paidOn), enabledOn, frozenOn).forEach(planned -> {
            covered.add(planned.period());
            issuedOn.add(today);
        });
    }

    private static void oracleClose(Set<LocalDate> oracle, LocalDate due, int grace, LocalDate today, Predicate<LocalDate> enabledOn, Predicate<LocalDate> frozenOn) {
        if (today.isAfter(due.plusDays(grace))) {
            addChargeableDays(oracle, due, today.withDayOfMonth(1).minusDays(1), enabledOn, frozenOn);
        }
    }

    private static void oracleSettlement(Set<LocalDate> oracle, LocalDate due, int grace, LocalDate paidOn, Predicate<LocalDate> enabledOn, Predicate<LocalDate> frozenOn) {
        if (paidOn.isAfter(due.plusDays(grace))) {
            addChargeableDays(oracle, due, paidOn, enabledOn, frozenOn);
        }
    }

    private static void addChargeableDays(Set<LocalDate> oracle, LocalDate due, LocalDate lastDay, Predicate<LocalDate> enabledOn, Predicate<LocalDate> frozenOn) {
        for (LocalDate day = due.plusDays(1); !day.isAfter(lastDay); day = day.plusDays(1)) {
            if (enabledOn.test(day) && !frozenOn.test(day)) {
                oracle.add(day);
            }
        }
    }

    private static Set<LocalDate> chargedDaysOf(Outcome outcome, Predicate<LocalDate> enabledOn, Predicate<LocalDate> frozenOn) {
        Set<LocalDate> seen = new HashSet<>();
        Set<LocalDate> charged = new HashSet<>();
        for (LateFeeAccrualPeriod period : outcome.covered()) {
            assertThat(period.firstDay()).isAfter(outcome.dueDate());
            for (LocalDate day = period.firstDay(); !day.isAfter(period.lastDay()); day = day.plusDays(1)) {
                assertThat(seen.add(day)).as("day %s covered twice", day).isTrue();
                if (enabledOn.test(day) && !frozenOn.test(day)) {
                    charged.add(day);
                }
            }
        }
        return charged;
    }

    @Property(tries = 1000, seed = "20261012")
    void plannerMatchesTheDayByDayOracleWithPaymentsAndReversals(@ForAll("scenarios") Scenario scenario) {
        Outcome outcome = simulate(scenario);
        LocalDate enabledFrom = START.plusDays(scenario.enabledFromOffsetDays());
        Optional<LocalDate> end = scenario.endOffsetDays().map(START::plusDays);
        Predicate<LocalDate> enabledOn = day -> !day.isBefore(enabledFrom);
        Predicate<LocalDate> frozenOn = day -> end.map(day::isAfter).orElse(false);

        Set<LocalDate> charged = chargedDaysOf(outcome, enabledOn, frozenOn);

        assertThat(charged).isEqualTo(outcome.oracleChargedDays());
        assertThat(outcome.overchargedDaysReported()).isEqualTo(outcome.overchargedDaysExpected());
    }

    @Property(tries = 1000, seed = "20261013")
    void atMostOneDebitNotePerMonthUnlessAReversalHappenedBetweenThem(@ForAll("scenarios") Scenario scenario) {
        Outcome outcome = simulate(scenario);

        for (int i = 0; i < outcome.covered().size(); i++) {
            for (int j = i + 1; j < outcome.covered().size(); j++) {
                LateFeeAccrualPeriod first = outcome.covered().get(i);
                LateFeeAccrualPeriod second = outcome.covered().get(j);
                if (first.accrualMonth().equals(second.accrualMonth())) {
                    LocalDate secondIssuedOn = outcome.issuedOn().get(j);
                    boolean reversedBetween = outcome.reversalDates().stream().anyMatch(day -> !day.isBefore(first.lastDay()) && !day.isAfter(secondIssuedOn));
                    assertThat(reversedBetween).as("periods %s and %s share a month without a reversal between them", first, second).isTrue();
                }
            }
        }
    }

    @Property(tries = 1000, seed = "20261014")
    void everyChargeableDayUpToTheCutoffOfTheFinalStateIsCovered(@ForAll("scenarios") Scenario scenario) {
        Outcome outcome = simulate(scenario);
        LocalDate enabledFrom = START.plusDays(scenario.enabledFromOffsetDays());
        Optional<LocalDate> end = scenario.endOffsetDays().map(START::plusDays);
        Predicate<LocalDate> enabledOn = day -> !day.isBefore(enabledFrom);
        Predicate<LocalDate> frozenOn = day -> end.map(day::isAfter).orElse(false);
        LocalDate cutoff = outcome.paid() ? outcome.paidOn() : outcome.lastDay().withDayOfMonth(1).minusDays(1);
        boolean pastGrace = (outcome.paid() ? outcome.paidOn() : outcome.lastDay()).isAfter(outcome.dueDate().plusDays(scenario.graceDays()));
        Set<LocalDate> charged = chargedDaysOf(outcome, enabledOn, frozenOn);

        if (pastGrace) {
            for (LocalDate day = outcome.dueDate().plusDays(1); !day.isAfter(cutoff); day = day.plusDays(1)) {
                if (enabledOn.test(day) && !frozenOn.test(day)) {
                    assertThat(charged).as("chargeable day %s not covered", day).contains(day);
                }
            }
        }
    }
}
