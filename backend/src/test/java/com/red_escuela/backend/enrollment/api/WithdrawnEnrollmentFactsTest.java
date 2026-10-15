package com.red_escuela.backend.enrollment.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-296")
class WithdrawnEnrollmentFactsTest {

    private static final LocalDate DATE = LocalDate.of(2027, 5, 15);

    @Test
    void programEnrollmentFactsExposeOnlyTheProgramEnrollment() {
        WithdrawnEnrollmentFacts facts = WithdrawnEnrollmentFacts.ofProgramEnrollment(1L, 10L, DATE, DATE, WithdrawalCause.VOLUNTARY);

        assertThat(facts.programEnrollment()).contains(10L);
        assertThat(facts.courseEnrollment()).isEmpty();
    }

    @Test
    void courseEnrollmentFactsExposeOnlyTheCourseEnrollment() {
        WithdrawnEnrollmentFacts facts = WithdrawnEnrollmentFacts.ofCourseEnrollment(1L, 20L, DATE, DATE, WithdrawalCause.NONPAYMENT);

        assertThat(facts.courseEnrollment()).contains(20L);
        assertThat(facts.programEnrollment()).isEmpty();
    }

    @Test
    void factsWithBothEnrollmentsAreRejected() {
        assertThatThrownBy(() -> new WithdrawnEnrollmentFacts(1L, 10L, 20L, DATE, DATE, WithdrawalCause.VOLUNTARY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factsWithNoEnrollmentAreRejected() {
        assertThatThrownBy(() -> new WithdrawnEnrollmentFacts(1L, null, null, DATE, DATE, WithdrawalCause.VOLUNTARY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factsWithoutStudentDateOrCauseAreRejected() {
        assertThatThrownBy(() -> WithdrawnEnrollmentFacts.ofProgramEnrollment(null, 10L, DATE, DATE, WithdrawalCause.VOLUNTARY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WithdrawnEnrollmentFacts.ofProgramEnrollment(1L, 10L, null, DATE, WithdrawalCause.VOLUNTARY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WithdrawnEnrollmentFacts.ofProgramEnrollment(1L, 10L, DATE, DATE, null)).isInstanceOf(NullPointerException.class);
    }
}
