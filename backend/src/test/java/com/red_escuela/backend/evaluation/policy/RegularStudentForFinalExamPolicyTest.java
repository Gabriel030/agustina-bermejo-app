package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegularStudentForFinalExamPolicyTest {

    private final RegularStudentForFinalExamPolicy policy = new RegularStudentForFinalExamPolicy();

    @Test
    void allowsARegularStudent() {
        PolicyVerdict verdict = policy.evaluate(new FinalExamLoadRequest(1L, EnrollmentStatus.REGULAR));

        assertThat(verdict.allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = "REGULAR", mode = EnumSource.Mode.EXCLUDE)
    void deniesAnyStatusOtherThanRegularWithTheViolationCode(EnrollmentStatus status) {
        PolicyVerdict verdict = policy.evaluate(new FinalExamLoadRequest(1L, status));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM");
        assertThat(verdict.reason()).isNotBlank();
    }

    @Test
    void deniesAnEnrollmentWithoutStatus() {
        assertThat(policy.evaluate(new FinalExamLoadRequest(1L, null)).allowed()).isFalse();
    }
}
