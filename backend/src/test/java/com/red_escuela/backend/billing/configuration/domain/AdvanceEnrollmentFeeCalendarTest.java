package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotDueThisMonthSpecification;
import com.red_escuela.backend.billing.planning.policy.PlanningFixtures_;
import com.red_escuela.backend.billing.configuration.service.SlotWindowResolver;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-528")
class AdvanceEnrollmentFeeCalendarTest {
    private static final SlotWindow WINDOW = new SlotWindow(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 28));

    private static YearCycle cycle(int leadMonths, LocalDate opensOn) {
        ProgramYearBillingTemplate template = PlanningFixtures_.template(1L, 2027);
        YearBillingTerms terms = new YearBillingTerms(10, 1, 2, null, leadMonths, 10, 10, true, false, true, true, true, false, opensOn);
        return YearCycle.of(template, terms, 2027);
    }

    private static Term term(LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd, LocalDateTime start) {
        Term term = TermFixtures.newTerm();
        term.setEnrollmentStart(enrollmentStart);
        term.setEnrollmentEnd(enrollmentEnd);
        term.setStartDate(start);
        return term;
    }

    @Test
    void withoutAConfiguredOpeningTheWindowStartIsTheOpening() {
        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle(1, null)).orElseThrow();

        assertThat(calendar.advanceOpensOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(calendar.windowClosesOn()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void aConfiguredOpeningWinsOverTheWindowStart() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle(1, LocalDate.of(2026, 9, 1))).orElseThrow().advanceOpensOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void theRegularEmissionIsTheSameMonthThePlannerUsesForTheSlot() {
        YearCycle cycle = cycle(2, null);

        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle).orElseThrow();

        assertThat(calendar.regularEmissionStart()).isEqualTo(EnrollmentFeeSlotDueThisMonthSpecification.emissionMonth(WINDOW, cycle, 1).atDay(1));
        assertThat(calendar.regularEmissionStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void theDefaultOpeningIsLaterThanTheRegularEmissionSoTheAlertShows() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle(1, null)).orElseThrow().openingAfterRegularEmission()).isTrue();
    }

    @Test
    void anOpeningOnOrBeforeTheRegularEmissionClearsTheAlert() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle(1, LocalDate.of(2026, 11, 1))).orElseThrow().openingAfterRegularEmission()).isFalse();
        assertThat(AdvanceEnrollmentFeeCalendar.of(Optional.of(WINDOW), cycle(1, LocalDate.of(2026, 9, 1))).orElseThrow().openingAfterRegularEmission()).isFalse();
    }

    @Test
    void withoutAWindowThereIsNoCalendar() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(Optional.empty(), cycle(1, null))).isEmpty();
    }

    @Test
    void oneTermInTheSlotGivesItsOwnEnrollmentWindow() {
        Term only = term(LocalDateTime.of(2026, 12, 1, 0, 0), LocalDateTime.of(2027, 2, 28, 0, 0), LocalDateTime.of(2027, 3, 1, 0, 0));

        assertThat(SlotWindowResolver.windowOfAmong(List.of(only), cycle(1, null), 1)).contains(WINDOW);
    }

    @Test
    void severalTermsInTheSlotTakeTheEarliestOpeningAndTheEarliestClose() {
        Term first = term(LocalDateTime.of(2026, 12, 10, 0, 0), LocalDateTime.of(2027, 2, 20, 0, 0), LocalDateTime.of(2027, 3, 1, 0, 0));
        Term second = term(LocalDateTime.of(2026, 11, 20, 0, 0), null, LocalDateTime.of(2027, 8, 1, 0, 0));

        SlotWindow window = SlotWindowResolver.windowOfAmong(List.of(first, second), cycle(1, null), 1).orElseThrow();
        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(Optional.of(window), cycle(1, null)).orElseThrow();

        assertThat(window.opensOn()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(window.closesOn()).isEqualTo(LocalDate.of(2027, 2, 20));
        assertThat(calendar.regularEmissionStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void irregularTermsAndTermsWithoutDatesDoNotShapeTheWindow() {
        Term withoutDates = term(null, null, LocalDateTime.of(2027, 3, 1, 0, 0));
        Term regular = term(LocalDateTime.of(2026, 12, 1, 0, 0), LocalDateTime.of(2027, 2, 28, 0, 0), LocalDateTime.of(2027, 3, 1, 0, 0));

        assertThat(SlotWindowResolver.regularTerms(List.of(withoutDates, regular), Set.of())).containsExactly(regular);
    }

    @Test
    void theOpeningSurvivesTheJsonRoundTripAndOldDocumentsReadAsNull() {
        YearBillingTerms withOpening = cycle(1, LocalDate.of(2026, 9, 1)).yearTerms();
        String legacyJson = ConfigurationValuesCodec.toJson(cycle(1, null).yearTerms()).replace(",\"advanceEnrollmentFeeOpensOn\":null", "");

        assertThat(((YearBillingTerms) ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(withOpening))).advanceEnrollmentFeeOpensOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(legacyJson).doesNotContain("advanceEnrollmentFeeOpensOn");
        assertThat(((YearBillingTerms) ConfigurationValuesCodec.fromJson(legacyJson)).advanceEnrollmentFeeOpensOn()).isNull();
    }
}
