package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.shared.domain.TermDatesRequiredException;
import com.red_escuela.backend.shared.domain.TermEnrollmentWindowRequiredException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateTermRequestTest {

    private static final LocalDateTime START = LocalDateTime.of(2027, 3, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2027, 7, 31, 0, 0);
    private static final LocalDateTime OPENS = LocalDateTime.of(2027, 2, 1, 0, 0);
    private static final LocalDateTime CLOSES = LocalDateTime.of(2027, 2, 28, 0, 0);

    private static CreateTermRequest requestWith(LocalDateTime opens, LocalDateTime closes) {
        return new CreateTermRequest(2027, "Primer cuatrimestre", TermType.FIRST_SEMESTER, START, END, opens, closes);
    }

    @Test
    void requestWithBothEnrollmentBoundariesIsAccepted() {
        assertThat(requestWith(OPENS, CLOSES).enrollmentStart()).isEqualTo(OPENS);
    }

    @Test
    void requestWithoutEnrollmentStartIsRejected() {
        assertThatThrownBy(() -> requestWith(null, CLOSES)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void requestWithoutEnrollmentEndIsRejected() {
        assertThatThrownBy(() -> requestWith(OPENS, null)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void requestWithoutAnyEnrollmentBoundaryIsRejected() {
        assertThatThrownBy(() -> requestWith(null, null)).isInstanceOf(TermEnrollmentWindowRequiredException.class);
    }

    @Test
    void requestWithoutStartDateIsRejected() {
        assertThatThrownBy(() -> new CreateTermRequest(2027, "Primer cuatrimestre", TermType.FIRST_SEMESTER, null, END, OPENS, CLOSES))
                .isInstanceOf(TermDatesRequiredException.class);
    }

    @Test
    void requestWithoutEndDateIsRejected() {
        assertThatThrownBy(() -> new CreateTermRequest(2027, "Primer cuatrimestre", TermType.FIRST_SEMESTER, START, null, OPENS, CLOSES))
                .isInstanceOf(TermDatesRequiredException.class);
    }

    @Test
    void requestWithoutAnyDateIsRejectedWithDatesError() {
        assertThatThrownBy(() -> new CreateTermRequest(2027, "Primer cuatrimestre", TermType.FIRST_SEMESTER, null, null, null, null))
                .isInstanceOf(TermDatesRequiredException.class);
    }
}
