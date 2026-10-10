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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-239")
@Tag("D-293")
@Tag("D-294")
class LateFeeAccrualProperties {
    private static final LocalDate START = LocalDate.of(2027, 1, 1);

    record Scenario(LocalDate dueDate, int graceDays, LocalDate enabledFrom, Optional<LocalDate> disabledFrom, Optional<LocalDate> endDate,
                    List<Integer> runGaps) {
        boolean enabledOn(LocalDate date) {
            return !date.isBefore(enabledFrom) && disabledFrom.map(date::isBefore).orElse(true);
        }
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<LocalDate> dueDate = Arbitraries.integers().between(0, 400).map(START::plusDays);
        Arbitrary<Integer> grace = Arbitraries.integers().between(0, 15);
        Arbitrary<LocalDate> enabledFrom = Arbitraries.integers().between(0, 700).map(START::plusDays);
        Arbitrary<Optional<LocalDate>> disabledFrom = Arbitraries.integers().between(0, 900).map(START::plusDays).optional();
        Arbitrary<Optional<LocalDate>> endDate = Arbitraries.integers().between(0, 900).map(START::plusDays).optional();
        Arbitrary<List<Integer>> gaps = Arbitraries.integers().between(0, 70).list().ofMinSize(1).ofMaxSize(40);
        return Combinators.combine(dueDate, grace, enabledFrom, disabledFrom, endDate, gaps).as(Scenario::new);
    }

    @Property(tries = 1000, seed = "20261010")
    void accrualKeepsAtMostOneChargePerMonthNeverBeforeEnablementNorAfterTheEndAndNeverDrifts(@ForAll("scenarios") Scenario scenario) {
        LateFeeSchedule schedule = new LateFeeSchedule(scenario.dueDate(), scenario.graceDays());
        Map<Integer, LocalDate> issued = new HashMap<>();
        LocalDate today = scenario.dueDate();
        List<LocalDate> runs = new ArrayList<>();
        for (int gap : scenario.runGaps()) {
            today = today.plusDays(gap);
            runs.add(today);
            for (LateFeeMonth month : schedule.monthsWithFactDateOnOrBefore(today)) {
                boolean frozen = scenario.endDate().map(end -> month.factDate().isAfter(end)).orElse(false);
                if (!issued.containsKey(month.ordinal()) && scenario.enabledOn(month.factDate()) && !frozen) {
                    issued.put(month.ordinal(), month.factDate());
                }
            }
        }

        LocalDate lastRun = runs.get(runs.size() - 1);
        issued.forEach((ordinal, factDate) -> {
            assertThat(factDate).isEqualTo(schedule.firstLateFeeDate().plusMonths(ordinal - 1L));
            assertThat(factDate).isAfterOrEqualTo(scenario.enabledFrom());
            assertThat(factDate).isAfter(scenario.dueDate());
            assertThat(factDate).isBeforeOrEqualTo(lastRun);
            scenario.endDate().ifPresent(end -> assertThat(factDate).isBeforeOrEqualTo(end));
            scenario.disabledFrom().ifPresent(off -> assertThat(factDate).isBefore(off));
        });
        List<Integer> ordinals = issued.keySet().stream().sorted().toList();
        for (int i = 1; i < ordinals.size(); i++) {
            assertThat(issued.get(ordinals.get(i))).isAfter(issued.get(ordinals.get(i - 1)));
        }
    }

    @Property(tries = 500, seed = "20261011")
    void skippingRunsNeverChangesTheMonthsThatEventuallyAccrue(@ForAll("scenarios") Scenario scenario) {
        LateFeeSchedule schedule = new LateFeeSchedule(scenario.dueDate(), scenario.graceDays());
        LocalDate finalRun = scenario.dueDate().plusDays(scenario.runGaps().stream().mapToInt(Integer::intValue).sum());

        List<Integer> everyDay = new ArrayList<>();
        for (LocalDate day = scenario.dueDate(); !day.isAfter(finalRun); day = day.plusDays(1)) {
            for (LateFeeMonth month : schedule.monthsWithFactDateOnOrBefore(day)) {
                if (scenario.enabledOn(month.factDate()) && !everyDay.contains(month.ordinal())) {
                    everyDay.add(month.ordinal());
                }
            }
        }
        List<Integer> singleRun = schedule.monthsWithFactDateOnOrBefore(finalRun).stream().filter(month -> scenario.enabledOn(month.factDate())).map(LateFeeMonth::ordinal).toList();

        assertThat(singleRun).containsExactlyInAnyOrderElementsOf(everyDay);
    }
}
