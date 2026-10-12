package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.shared.domain.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentWindowTest {

    private static final LocalDateTime START = LocalDateTime.of(2027, 2, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2027, 2, 28, 23, 59);
    private static final LocalDateTime BEFORE = START.minusDays(1);
    private static final LocalDateTime DURING = START.plusDays(10);
    private static final LocalDateTime AFTER = END.plusDays(1);

    @Test
    void windowWithoutEndsIsAlwaysOpen() {
        EnrollmentWindow window = EnrollmentWindow.unbounded();

        assertThat(window.isOpenAt(BEFORE)).isTrue();
        assertThat(window.isOpenAt(AFTER)).isTrue();
    }

    @Test
    void windowWithOnlyAStartIsOpenFromThatInstant() {
        EnrollmentWindow window = new EnrollmentWindow(START, null);

        assertThat(window.isOpenAt(BEFORE)).isFalse();
        assertThat(window.isOpenAt(START)).isTrue();
        assertThat(window.isOpenAt(AFTER)).isTrue();
    }

    @Test
    void windowWithOnlyAnEndIsOpenUntilThatInstant() {
        EnrollmentWindow window = new EnrollmentWindow(null, END);

        assertThat(window.isOpenAt(BEFORE)).isTrue();
        assertThat(window.isOpenAt(END)).isTrue();
        assertThat(window.isOpenAt(AFTER)).isFalse();
    }

    @Test
    void boundedWindowIsOpenOnlyDuringItsRange() {
        EnrollmentWindow window = new EnrollmentWindow(START, END);

        assertThat(window.isOpenAt(BEFORE)).isFalse();
        assertThat(window.isOpenAt(DURING)).isTrue();
        assertThat(window.isOpenAt(AFTER)).isFalse();
    }

    @Test
    void closedOverrideShutsAnOtherwiseOpenWindow() {
        EnrollmentWindow window = new EnrollmentWindow(START, END);

        assertThat(window.isOpenAt(DURING, TermEnrollmentOverride.CLOSED)).isFalse();
    }

    @Test
    void openOverrideOpensAnOtherwiseClosedWindow() {
        EnrollmentWindow window = new EnrollmentWindow(START, END);

        assertThat(window.isOpenAt(AFTER, TermEnrollmentOverride.OPEN)).isTrue();
    }

    @Test
    void windowEndingBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> new EnrollmentWindow(END, START)).isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void termAnswersWhetherEnrollmentIsOpenUsingItsWindowAndOverride() {
        Term term = new Term();
        term.defineEnrollmentWindow(START, END);

        assertThat(term.isEnrollmentOpenAt(DURING)).isTrue();
        assertThat(term.isEnrollmentOpenAt(AFTER)).isFalse();

        term.setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        assertThat(term.isEnrollmentOpenAt(DURING)).isFalse();
        assertThat(term.isEnrollmentClosedByOverride()).isTrue();

        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        assertThat(term.isEnrollmentOpenAt(AFTER)).isTrue();
    }

    @Test
    void termRejectsAnInvertedEnrollmentWindow() {
        Term term = new Term();

        assertThatThrownBy(() -> term.defineEnrollmentWindow(END, START)).isInstanceOf(InvalidDateRangeException.class);
    }
}
