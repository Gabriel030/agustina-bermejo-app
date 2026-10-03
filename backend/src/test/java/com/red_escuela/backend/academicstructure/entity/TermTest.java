package com.red_escuela.backend.academicstructure.entity;

import com.red_escuela.backend.academicstructure.exception.InvalidTermDatesException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermTest {

    private static final LocalDateTime JANUARY = LocalDateTime.of(2027, 1, 1, 0, 0);
    private static final LocalDateTime DECEMBER = LocalDateTime.of(2027, 12, 31, 0, 0);

    @Test
    void termEndingBeforeItStartsIsRejected() {
        Term term = new Term();

        assertThatThrownBy(() -> term.reschedule(DECEMBER, JANUARY)).isInstanceOf(InvalidTermDatesException.class);
        assertThat(term.getStartDate()).isNull();
        assertThat(term.getEndDate()).isNull();
    }

    @Test
    void partialRescheduleKeepsTheOtherBoundAndValidatesTheResult() {
        Term term = new Term();
        term.reschedule(JANUARY, DECEMBER);

        assertThatThrownBy(() -> term.reschedule(null, LocalDateTime.of(2026, 6, 30, 0, 0)))
                .isInstanceOf(InvalidTermDatesException.class);
        assertThat(term.getEndDate()).isEqualTo(DECEMBER);

        term.reschedule(LocalDateTime.of(2027, 2, 1, 0, 0), null);
        assertThat(term.getStartDate()).isEqualTo(LocalDateTime.of(2027, 2, 1, 0, 0));
        assertThat(term.getEndDate()).isEqualTo(DECEMBER);
    }

    @Test
    void termStartingAndEndingTheSameDayIsValid() {
        Term term = new Term();

        assertThatCode(() -> term.reschedule(JANUARY, JANUARY)).doesNotThrowAnyException();
    }
}
