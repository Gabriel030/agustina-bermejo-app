package com.red_escuela.backend.academicstructure.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TermEnrollmentOverrideExpiryTest {

    private static final LocalDateTime TERM_END = LocalDateTime.of(2027, 7, 1, 23, 59);

    private Term termWithOpenOverride() {
        Term term = new Term();
        term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
        term.setEndDate(TERM_END);
        term.defineEnrollmentWindow(LocalDateTime.of(2027, 2, 1, 0, 0), LocalDateTime.of(2027, 3, 15, 0, 0));
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        return term;
    }

    @Test
    void openOverrideKeepsEnrollmentOpenWhileTheTermIsRunning() {
        Term term = termWithOpenOverride();

        assertThat(term.isEnrollmentOpenAt(LocalDateTime.of(2027, 5, 1, 10, 0))).isTrue();
        assertThat(term.hasEnrollmentEndedAt(LocalDateTime.of(2027, 5, 1, 10, 0))).isFalse();
    }

    @Test
    void openOverrideStopsOpeningEnrollmentOnceTheTermHasEnded() {
        Term term = termWithOpenOverride();
        LocalDateTime afterTermEnd = TERM_END.plusMinutes(1);

        assertThat(term.hasOpenOverrideExpiredAt(afterTermEnd)).isTrue();
        assertThat(term.isEnrollmentOpenAt(afterTermEnd)).isFalse();
        assertThat(term.hasEnrollmentEndedAt(afterTermEnd)).isTrue();
    }

    @Test
    void openOverrideStillAppliesAtTheExactTermEndMoment() {
        Term term = termWithOpenOverride();

        assertThat(term.hasOpenOverrideExpiredAt(TERM_END)).isFalse();
        assertThat(term.isEnrollmentOpenAt(TERM_END)).isTrue();
    }
}
