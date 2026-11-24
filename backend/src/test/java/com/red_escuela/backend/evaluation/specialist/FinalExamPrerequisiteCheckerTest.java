package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.api.PrerequisiteAssessment;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.evaluation.exception.FinalExamPrerequisitesNotApprovedException;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinalExamPrerequisiteCheckerTest {

    private static final Long PROGRAM_ENROLLMENT_ID = 1L;
    private static final Long SUBJECT_ID = 2L;
    private static final Long ENROLLMENT_ID = 3L;

    private final ProgramPrerequisiteEvaluator evaluator = mock(ProgramPrerequisiteEvaluator.class);
    private final FinalExamPrerequisiteChecker checker = new FinalExamPrerequisiteChecker(evaluator);

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstPending(Throwable failure) {
        return (Map<String, Object>) ((List<?>) ((FinalExamPrerequisitesNotApprovedException) failure)
                .details().get("pendingPrerequisites")).get(0);
    }

    @Test
    void pendingPrerequisiteBelowTheMinimumExposesMinimumAndScore() {
        when(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(PROGRAM_ENROLLMENT_ID, SUBJECT_ID)).thenReturn(List.of(
                new PrerequisiteAssessment(10L, "Algebra I", RequirementType.APPROVED, PrerequisiteStanding.APPROVED,
                        false, grade("6"), grade("5"))));

        assertThatThrownBy(() -> checker.requireAllApprovedOrWaived(PROGRAM_ENROLLMENT_ID, SUBJECT_ID, ENROLLMENT_ID))
                .isInstanceOf(FinalExamPrerequisitesNotApprovedException.class)
                .satisfies(failure -> assertThat(firstPending(failure))
                        .containsEntry("subjectName", "Algebra I")
                        .containsEntry("standing", "APPROVED")
                        .containsEntry("minGrade", grade("6").value())
                        .containsEntry("approvedScore", grade("5").value()));
    }

    @Test
    void pendingPrerequisiteWithoutMinimumOmitsGradeFieldsInsteadOfFailing() {
        when(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(PROGRAM_ENROLLMENT_ID, SUBJECT_ID)).thenReturn(List.of(
                new PrerequisiteAssessment(10L, "Algebra I", RequirementType.PASSED_COURSEWORK,
                        PrerequisiteStanding.REGULAR, true, null, null)));

        assertThatThrownBy(() -> checker.requireAllApprovedOrWaived(PROGRAM_ENROLLMENT_ID, SUBJECT_ID, ENROLLMENT_ID))
                .isInstanceOf(FinalExamPrerequisitesNotApprovedException.class)
                .satisfies(failure -> assertThat(firstPending(failure))
                        .containsEntry("standing", "REGULAR")
                        .doesNotContainKeys("minGrade", "approvedScore"));
    }

    @Test
    void noPendingPrerequisitesDoesNotThrow() {
        when(evaluator.findUnmetForFinalExamOrEmptyOutsidePlan(PROGRAM_ENROLLMENT_ID, SUBJECT_ID)).thenReturn(List.of());

        assertThatCode(() -> checker.requireAllApprovedOrWaived(PROGRAM_ENROLLMENT_ID, SUBJECT_ID, ENROLLMENT_ID))
                .doesNotThrowAnyException();
    }
}
