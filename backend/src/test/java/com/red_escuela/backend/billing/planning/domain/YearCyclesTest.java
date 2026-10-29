package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.academicstructure.entity.Term;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YearCyclesTest {

    private static YearCycle cycle(long id, LocalDateTime start, LocalDateTime end) {
        Term term = new Term();
        term.setYear(start.getYear());
        term.setStartDate(start);
        term.setEndDate(end);
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setId(id);
        template.setTerm(term);
        return TestTemplateTerms.cycleOf(template, start.getYear());
    }

    private static YearCycle year(long id, int year) {
        return cycle(id, LocalDateTime.of(year, 3, 1, 0, 0), LocalDateTime.of(year, 12, 31, 23, 59));
    }

    @Test
    void cyclesAreSortedByStartAndEachCoverageStartsRightAfterThePreviousCycleEnds() {
        YearCycle y2025 = year(1L, 2025);
        YearCycle y2027 = year(2L, 2027);

        YearCycles cycles = new YearCycles(List.of(y2027, y2025), null);

        assertThat(cycles.all()).extracting(YearCycle::templateId).containsExactly(1L, 2L);
        assertThat(cycles.all().get(0).start()).isEqualTo(y2025.start());
        assertThat(cycles.all().get(1).start()).isEqualTo(y2027.start());
        assertThat(cycles.covering(YearMonth.of(2026, 6))).isEmpty();
        assertThat(cycles.covering(y2027.start()).map(YearCycle::templateId)).contains(2L);
        assertThat(cycles.covering(y2027.start().minusMonths(1))).isEmpty();
    }

    @Test
    void aGapExistsOnlyInAYearTouchedByNoCycleThatLiesBetweenOthers() {
        YearCycles cycles = new YearCycles(List.of(year(1L, 2025), year(2L, 2027)), null);

        assertThat(cycles.hasGapAt(YearMonth.of(2026, 6))).isTrue();
        assertThat(cycles.hasGapAt(YearMonth.of(2025, 6))).isFalse();
        assertThat(cycles.hasGapAt(YearMonth.of(2027, 6))).isFalse();
        assertThat(cycles.hasGapAt(YearMonth.of(2024, 6))).isFalse();
        assertThat(cycles.hasGapAt(YearMonth.of(2028, 6))).isFalse();
    }

    @Test
    void aMonthIsConfiguredWhileSomeCycleEndsOnOrAfterIt() {
        YearCycles cycles = new YearCycles(List.of(year(1L, 2025)), null);

        assertThat(cycles.isConfiguredFor(YearMonth.of(2025, 12))).isTrue();
        assertThat(cycles.isConfiguredFor(YearMonth.of(2026, 1))).isFalse();
    }

    @Test
    void theLatestCycleStartedOnAMonthIncludesACycleStartingExactlyThatMonth() {
        YearCycle y2025 = year(1L, 2025);
        YearCycle y2027 = year(2L, 2027);
        YearCycles cycles = new YearCycles(List.of(y2025, y2027), null);

        assertThat(cycles.latestStartedOn(y2025.start().minusMonths(1))).isEmpty();
        assertThat(cycles.latestStartedOn(y2025.start()).map(YearCycle::templateId)).contains(1L);
        assertThat(cycles.latestStartedOn(y2027.start().minusMonths(1)).map(YearCycle::templateId)).contains(1L);
        assertThat(cycles.latestStartedOn(y2027.start()).map(YearCycle::templateId)).contains(2L);
        assertThat(cycles.latestStartedOn(YearMonth.of(2030, 1)).map(YearCycle::templateId)).contains(2L);
    }

    @Test
    void whenCyclesOverlapTheOneWithTheLatestTermStartWinsUnlessAPreferredTemplateMatches() {
        YearCycle older = cycle(1L, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59));
        YearCycle newer = cycle(2L, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2027, 6, 30, 23, 59));
        YearMonth overlap = YearMonth.of(2026, 10);

        assertThat(new YearCycles(List.of(older, newer), null).covering(overlap).map(YearCycle::templateId)).contains(2L);
        assertThat(new YearCycles(List.of(older, newer), 1L).covering(overlap).map(YearCycle::templateId)).contains(1L);
        assertThat(new YearCycles(List.of(older, newer), 1L).forInstallment(overlap).map(YearCycle::templateId)).contains(1L);
        assertThat(new YearCycles(List.of(older, newer), 99L).forInstallment(overlap).map(YearCycle::templateId)).contains(2L);
        assertThat(new YearCycles(List.of(older, newer), 1L).covering(YearMonth.of(2027, 2)).map(YearCycle::templateId)).contains(2L);
        assertThat(new YearCycles(List.of(older, newer), 1L).covering(YearMonth.of(2029, 2))).isEmpty();
    }
}
