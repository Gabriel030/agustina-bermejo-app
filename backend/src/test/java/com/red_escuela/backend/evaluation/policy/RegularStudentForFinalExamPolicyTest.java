package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegularStudentForFinalExamPolicyTest {

    private final RegularStudentForFinalExamPolicy policy = new RegularStudentForFinalExamPolicy();

    @Test
    void allowsARegularStudent() {
        PolicyVerdict verdict = policy.evaluate(new FinalExamLoadRequest(1L, true));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void deniesAStudentWhoIsNotRegularWithTheViolationCode() {
        PolicyVerdict verdict = policy.evaluate(new FinalExamLoadRequest(1L, false));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM");
        assertThat(verdict.reason()).isNotBlank();
    }
}
