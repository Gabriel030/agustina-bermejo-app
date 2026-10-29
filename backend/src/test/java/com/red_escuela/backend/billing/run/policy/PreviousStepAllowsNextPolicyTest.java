package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-449")
class PreviousStepAllowsNextPolicyTest {
    private final PreviousStepAllowsNextPolicy policy = new PreviousStepAllowsNextPolicy();

    @ParameterizedTest
    @EnumSource(value = BillingRunStatus.class, names = {"COMPLETED", "COMPLETED_WITH_ERRORS", "NOT_APPLICABLE", "RUNNING"})
    void theNextStepRunsAfterAnyStatusThatIsNotAFailure(BillingRunStatus previous) {
        assertThat(policy.evaluateRule(new PreviousStepContext(previous)).allowed()).isTrue();
    }

    @Test
    void theNextStepDoesNotRunAfterAStepThatFailedEntirely() {
        var verdict = policy.evaluateRule(new PreviousStepContext(BillingRunStatus.FAILED));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("PREVIOUS_STEP_FAILED");
    }

    @Test
    void theFirstStepAlwaysRuns() {
        assertThat(policy.evaluateRule(PreviousStepContext.beforeTheFirstStep()).allowed()).isTrue();
    }
}
