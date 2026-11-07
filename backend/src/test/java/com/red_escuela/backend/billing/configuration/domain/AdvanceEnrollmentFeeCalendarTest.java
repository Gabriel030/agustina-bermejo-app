package com.red_escuela.backend.billing.configuration.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-528")
class AdvanceEnrollmentFeeCalendarTest {
    private static final LocalDateTime WINDOW_OPENS = LocalDateTime.of(2026, 12, 1, 0, 0);
    private static final LocalDateTime WINDOW_CLOSES = LocalDateTime.of(2027, 2, 28, 0, 0);
    private static final LocalDateTime TERM_START = LocalDateTime.of(2027, 3, 1, 0, 0);

    private static YearBillingTerms terms(LocalDate opensOn) {
        return new YearBillingTerms(10, 1, 11, null, 1, 10, 10, true, false, true, true, true, false, opensOn);
    }

    @Test
    void withoutAConfiguredOpeningTheWindowStartIsTheOpening() {
        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(null)).orElseThrow();

        assertThat(calendar.advanceOpensOn()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(calendar.windowClosesOn()).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void aConfiguredOpeningWinsOverTheWindowStart() {
        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(LocalDate.of(2026, 9, 1))).orElseThrow();

        assertThat(calendar.advanceOpensOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void theRegularEmissionStartsTheConfiguredLeadMonthsBeforeTheWindowOpens() {
        AdvanceEnrollmentFeeCalendar calendar = AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(null)).orElseThrow();

        assertThat(calendar.regularEmissionStart()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void theDefaultOpeningIsLaterThanTheRegularEmissionSoTheAlertShows() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(null)).orElseThrow().openingAfterRegularEmission()).isTrue();
    }

    @Test
    void anOpeningOnOrBeforeTheRegularEmissionClearsTheAlert() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(LocalDate.of(2026, 11, 1))).orElseThrow().openingAfterRegularEmission()).isFalse();
        assertThat(AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, WINDOW_CLOSES, TERM_START, terms(LocalDate.of(2026, 9, 1))).orElseThrow().openingAfterRegularEmission()).isFalse();
    }

    @Test
    void withoutEnrollmentDatesThereIsNoCalendar() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(null, WINDOW_CLOSES, TERM_START, terms(null))).isEmpty();
        assertThat(AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, null, null, terms(null))).isEmpty();
    }

    @Test
    void aMissingWindowEndFallsBackToTheTermStart() {
        assertThat(AdvanceEnrollmentFeeCalendar.of(WINDOW_OPENS, null, TERM_START, terms(null)).orElseThrow().windowClosesOn()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void theOpeningSurvivesTheJsonRoundTripAndOldDocumentsReadAsNull() {
        YearBillingTerms withOpening = terms(LocalDate.of(2026, 9, 1));
        String legacyJson = ConfigurationValuesCodec.toJson(terms(null)).replace(",\"advanceEnrollmentFeeOpensOn\":null", "");

        assertThat(((YearBillingTerms) ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(withOpening))).advanceEnrollmentFeeOpensOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(legacyJson).doesNotContain("advanceEnrollmentFeeOpensOn");
        assertThat(((YearBillingTerms) ConfigurationValuesCodec.fromJson(legacyJson)).advanceEnrollmentFeeOpensOn()).isNull();
    }
}
