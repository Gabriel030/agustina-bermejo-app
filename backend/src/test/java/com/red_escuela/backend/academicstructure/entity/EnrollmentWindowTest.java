package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.shared.domain.TermEnrollmentWindowRequiredException;
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
    void windowWithoutStartIsRejected() {
        assertThatThrownBy(() -> new EnrollmentWindow(null, END)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void windowWithoutEndIsRejected() {
        assertThatThrownBy(() -> new EnrollmentWindow(START, null)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void windowIsOpenAtBothBoundaryInstants() {
        EnrollmentWindow window = new EnrollmentWindow(START, END);

        assertThat(window.isOpenAt(START)).isTrue();
        assertThat(window.isOpenAt(END)).isTrue();
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
    void termCannotLoseItsEnrollmentWindowWhenNoneIsGiven() {
        Term term = new Term();

        assertThatThrownBy(() -> term.defineEnrollmentWindow(START, null)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void termKeepsItsWindowWhenOnlyOneEndIsRedefined() {
        Term term = new Term();
        term.defineEnrollmentWindow(START, END);

        term.defineEnrollmentWindow(null, END.plusDays(5));

        assertThat(term.enrollmentWindow()).isEqualTo(new EnrollmentWindow(START, END.plusDays(5)));
    }

    @Test
    void termRejectsAnInvertedEnrollmentWindow() {
        Term term = new Term();

        assertThatThrownBy(() -> term.defineEnrollmentWindow(END, START)).isInstanceOf(InvalidDateRangeException.class);
    }
}
