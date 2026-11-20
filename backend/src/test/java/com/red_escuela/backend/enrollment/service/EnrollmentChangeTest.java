package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.exception.EnrollmentChangeReasonInvalidException;
import com.red_escuela.backend.enrollment.exception.EnrollmentChangeReasonRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentChangeTest {

    @Test
    void nullOrBlankReasonIsRequired() {
        assertThatThrownBy(() -> EnrollmentChange.byStaff(null, 1L)).isInstanceOf(EnrollmentChangeReasonRequiredException.class);
        assertThatThrownBy(() -> EnrollmentChange.byStaff("   \n\t", 1L)).isInstanceOf(EnrollmentChangeReasonRequiredException.class);
    }

    @Test
    void reasonShorterThanTenCharactersAfterNormalizingIsInvalid() {
        assertThatThrownBy(() -> EnrollmentChange.byStaff("  abc   def  ", 1L)).isInstanceOf(EnrollmentChangeReasonInvalidException.class);
        assertThatThrownBy(() -> EnrollmentChange.byStaff("123456789", 1L)).isInstanceOf(EnrollmentChangeReasonInvalidException.class);
    }

    @Test
    void reasonOfExactlyTenAndFiveHundredCharactersIsAccepted() {
        assertThat(EnrollmentChange.byStaff("1234567890", 1L).reason()).hasSize(10);
        assertThat(EnrollmentChange.byStaff("a".repeat(500), 1L).reason()).hasSize(500);
    }

    @Test
    void reasonLongerThanFiveHundredCharactersIsInvalid() {
        assertThatThrownBy(() -> EnrollmentChange.byStaff("a".repeat(501), 1L)).isInstanceOf(EnrollmentChangeReasonInvalidException.class);
    }

    @Test
    void reasonIsTrimmedAndInnerWhitespaceCollapsed() {
        assertThat(EnrollmentChange.byStaff("  Cambio   de\n turno  ", 1L).reason()).isEqualTo("Cambio de turno");
    }

    @Test
    void studentChangeHasNoReasonAndIsNotMadeByStaff() {
        EnrollmentChange change = EnrollmentChange.byStudent();

        assertThat(change.reason()).isNull();
        assertThat(change.madeByStaff()).isFalse();
    }

    @Test
    void voluntaryWithdrawalOfProgramOrCourseRequiresTheReason() {
        assertThatThrownBy(() -> EnrollmentWithdrawal.voluntaryRegisteredBy(null, 1L, null)).isInstanceOf(EnrollmentChangeReasonRequiredException.class);
        assertThat(EnrollmentWithdrawal.voluntaryRegisteredBy(null, 1L, " Se mudó de ciudad ").reason()).isEqualTo("Se mudó de ciudad");
    }
}
