package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResponse;
import com.red_escuela.backend.enrollment.api.FinalExamIneligibilityReason;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegularStudentForFinalExamPolicyTest {

    private final RegularStudentForFinalExamPolicy policy = new RegularStudentForFinalExamPolicy();

    @Test
    void allowsARegularStudent() {
        PolicyVerdict verdict = policy.evaluateRule(new FinalExamEligibilityRequest(1L, FinalExamEligibilityResponse.allowed()));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void ignoresAStudentBlockedOnlyByAbsences() {
        PolicyVerdict verdict = policy.evaluateRule(new FinalExamEligibilityRequest(
                1L, FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.FREE_BY_ABSENCES)));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void deniesAStudentWhoIsNotRegularWithTheViolationCode() {
        PolicyVerdict verdict = policy.evaluateRule(new FinalExamEligibilityRequest(1L, FinalExamEligibilityResponse.blockedBy(FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR)));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM");
        assertThat(verdict.reason()).isNotBlank();
    }
}
