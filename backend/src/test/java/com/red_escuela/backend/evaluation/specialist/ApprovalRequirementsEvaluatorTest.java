package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ApprovalRequirement;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementCheck;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalRequirementsEvaluatorTest {

    private static ApprovalRequirementContext contextWithPrerequisitesMet(boolean met) {
        return new ApprovalRequirementContext(() -> met);
    }

    @Test
    void givenNoChecks_whenEvaluating_thenNothingIsUnmet() {
        ApprovalRequirementsEvaluator evaluator = new ApprovalRequirementsEvaluator(List.of());

        assertThat(evaluator.evaluate(contextWithPrerequisitesMet(false)).isEmpty()).isTrue();
    }

    @Test
    void givenPrerequisitesNotMet_whenEvaluating_thenPrerequisitesAreUnmet() {
        ApprovalRequirementsEvaluator evaluator = new ApprovalRequirementsEvaluator(
                List.of(new PrerequisitesApprovalRequirementCheck()));

        assertThat(evaluator.evaluate(contextWithPrerequisitesMet(false)).requirements())
                .containsExactly(ApprovalRequirement.PREREQUISITES);
    }

    @Test
    void givenPrerequisitesMet_whenEvaluating_thenNothingIsUnmet() {
        ApprovalRequirementsEvaluator evaluator = new ApprovalRequirementsEvaluator(
                List.of(new PrerequisitesApprovalRequirementCheck()));

        assertThat(evaluator.evaluate(contextWithPrerequisitesMet(true)).isEmpty()).isTrue();
    }

    @Test
    void givenCheckThatIsSatisfied_whenEvaluatingAlongsideUnmetCheck_thenOnlyUnmetIsReported() {
        ApprovalRequirementCheck satisfied = new ApprovalRequirementCheck() {
            @Override
            public ApprovalRequirement requirement() {
                return ApprovalRequirement.PREREQUISITES;
            }

            @Override
            public boolean isUnmet(ApprovalRequirementContext context) {
                return false;
            }
        };
        ApprovalRequirementsEvaluator evaluator = new ApprovalRequirementsEvaluator(List.of(satisfied));

        assertThat(evaluator.evaluate(contextWithPrerequisitesMet(false)).isEmpty()).isTrue();
    }
}
