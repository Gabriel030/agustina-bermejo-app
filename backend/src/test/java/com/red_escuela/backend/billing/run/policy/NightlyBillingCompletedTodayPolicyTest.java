package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-055")
@Tag("D-459")
class NightlyBillingCompletedTodayPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 10, 11);
    private final NightlyBillingCompletedTodayPolicy policy = new NightlyBillingCompletedTodayPolicy();

    private PolicyVerdict evaluate(LocalDate runDate, NightlyBillingRunStatus status) {
        return policy.evaluateRule(new NightlyBillingCompletedTodayContext(TODAY, runDate, status));
    }

    @Test
    void aCompletedRunOfTodayIsAllowed() {
        assertThat(evaluate(TODAY, NightlyBillingRunStatus.COMPLETED).allowed()).isTrue();
    }

    @Test
    void aRunOfTodayCompletedWithErrorsIsAllowed() {
        assertThat(evaluate(TODAY, NightlyBillingRunStatus.COMPLETED_WITH_ERRORS).allowed()).isTrue();
    }

    @Test
    void aFailedRunOfTodayIsDenied() {
        PolicyVerdict verdict = evaluate(TODAY, NightlyBillingRunStatus.FAILED);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("NIGHTLY_BILLING_NOT_COMPLETED");
    }

    @Test
    void aRunningRunOfTodayIsDenied() {
        assertThat(evaluate(TODAY, NightlyBillingRunStatus.RUNNING).allowed()).isFalse();
    }

    @Test
    void aCompletedRunOfYesterdayIsDenied() {
        assertThat(evaluate(TODAY.minusDays(1), NightlyBillingRunStatus.COMPLETED).allowed()).isFalse();
    }

    @Test
    void theAbsenceOfAnyRunIsDenied() {
        assertThat(policy.evaluateRule(NightlyBillingCompletedTodayContext.withoutAnyRun(TODAY)).allowed()).isFalse();
    }

    @Test
    void theVerdictsCarryTheRuleName() {
        assertThat(policy.ruleName()).isEqualTo("NightlyBillingCompletedTodayPolicy");
        assertThat(evaluate(TODAY, NightlyBillingRunStatus.COMPLETED).policy()).isEqualTo("NightlyBillingCompletedTodayPolicy");
        assertThat(evaluate(TODAY, NightlyBillingRunStatus.FAILED).policy()).isEqualTo("NightlyBillingCompletedTodayPolicy");
    }
}
