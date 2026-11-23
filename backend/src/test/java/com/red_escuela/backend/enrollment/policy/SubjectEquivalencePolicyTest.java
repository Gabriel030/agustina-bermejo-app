package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.exception.EnrollmentNotActiveException;
import com.red_escuela.backend.enrollment.exception.EquivalenceAlreadyVoidedException;
import com.red_escuela.backend.enrollment.exception.EquivalenceReasonInvalidException;
import com.red_escuela.backend.enrollment.exception.FutureDateException;
import com.red_escuela.backend.enrollment.exception.InvalidScoreException;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectEquivalencePolicyTest {

    @Test
    void enrolledEnrollmentIsActive() {
        assertThatCode(() -> SubjectEquivalencePolicy.requireActiveEnrollment(1L, ProgramEnrollmentStatus.ENROLLED))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = ProgramEnrollmentStatus.class, names = "ENROLLED", mode = EnumSource.Mode.EXCLUDE)
    void nonEnrolledStatusesAreNotActive(ProgramEnrollmentStatus status) {
        assertThatThrownBy(() -> SubjectEquivalencePolicy.requireActiveEnrollment(1L, status))
                .isInstanceOf(EnrollmentNotActiveException.class);
    }

    @Test
    void missingScoreMeansNoGrade() {
        assertThat(SubjectEquivalencePolicy.parseOriginScore(null)).isNull();
    }

    @Test
    void scoreBoundariesAreAccepted() {
        assertThat(SubjectEquivalencePolicy.parseOriginScore(new BigDecimal("1"))).isEqualTo(Grade.of(BigDecimal.ONE));
        assertThat(SubjectEquivalencePolicy.parseOriginScore(new BigDecimal("10.00"))).isEqualTo(Grade.of(BigDecimal.TEN));
    }

    @Test
    void scoreOutsideRangeOrWithTooManyDecimalsIsInvalid() {
        for (String raw : new String[] {"0.99", "10.01", "0", "-3", "7.555"}) {
            assertThatThrownBy(() -> SubjectEquivalencePolicy.parseOriginScore(new BigDecimal(raw)))
                    .as(raw).isInstanceOf(InvalidScoreException.class);
        }
    }

    @Test
    void todayIsAcceptedAndTomorrowIsFuture() {
        LocalDate today = LocalDate.of(2026, 5, 10);
        assertThatCode(() -> SubjectEquivalencePolicy.requireNotFuture(today, today)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SubjectEquivalencePolicy.requireNotFuture(today.plusDays(1), today))
                .isInstanceOf(FutureDateException.class);
    }

    @Test
    void reasonIsTrimmedAndBoundsAreInclusive() {
        assertThat(SubjectEquivalencePolicy.normalizeReason("  0123456789  ")).isEqualTo("0123456789");
        assertThat(SubjectEquivalencePolicy.normalizeReason("x".repeat(500))).hasSize(500);
    }

    @Test
    void reasonOutsideBoundsIsInvalid() {
        for (String raw : new String[] {null, "", "         ", "012345678", "x".repeat(501)}) {
            assertThatThrownBy(() -> SubjectEquivalencePolicy.normalizeReason(raw))
                    .isInstanceOf(EquivalenceReasonInvalidException.class);
        }
    }

    @Test
    void voidedEquivalenceCannotBeVoidedAgain() {
        SubjectEquivalence voided = SubjectEquivalence.builder().id(5L).status(SubjectEquivalenceStatus.VOIDED).build();
        SubjectEquivalence active = SubjectEquivalence.builder().id(6L).build();

        assertThatThrownBy(() -> SubjectEquivalencePolicy.requireActive(voided)).isInstanceOf(EquivalenceAlreadyVoidedException.class);
        assertThatCode(() -> SubjectEquivalencePolicy.requireActive(active)).doesNotThrowAnyException();
    }
}
