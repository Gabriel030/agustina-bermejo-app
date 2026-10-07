package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.exception.FinalExamRequiresRegularStudentException;
import com.red_escuela.backend.evaluation.service.FinalExamEligibilityEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinalExamEligibilityEvaluatorTest {

    private final FinalExamEligibilityEvaluator evaluator = new FinalExamEligibilityEvaluator();

    @Test
    void doesNothingForARegularStudent() {
        assertThatCode(() -> evaluator.requireEligible(1L, EnrollmentStatus.REGULAR)).doesNotThrowAnyException();
    }

    @Test
    void throwsTheBusinessExceptionCarryingTheViolationForANonRegularStudent() {
        assertThatThrownBy(() -> evaluator.requireEligible(1L, EnrollmentStatus.IN_PROGRESS))
                .isInstanceOfSatisfying(FinalExamRequiresRegularStudentException.class, exception ->
                        assertThat(exception.violations()).extracting("code")
                                .containsExactly("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM"));
    }
}
