package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-065")
@Tag("D-292")
class NewEnrollmentBlockedByOverdueDebtPolicyTest {

    private final NewEnrollmentBlockedByOverdueDebtPolicy policy = new NewEnrollmentBlockedByOverdueDebtPolicy();

    private PolicyVerdict verdictFor(boolean blockWhenOverdue, boolean overdueDebt) {
        return policy.evaluateRule(new NewEnrollmentDebtCheck(1L, blockWhenOverdue, overdueDebt));
    }

    @Test
    void overdueDebtBlocksWhenTheRuleIsEnabled() {
        PolicyVerdict verdict = verdictFor(true, true);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("OVERDUE_DEBT_BLOCKS_NEW_ENROLLMENT");
    }

    @Test
    void overdueDebtDoesNotBlockWhenTheRuleIsDisabled() {
        assertThat(verdictFor(false, true).allowed()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void withoutOverdueDebtNothingBlocks(boolean blockWhenOverdue) {
        assertThat(verdictFor(blockWhenOverdue, false).allowed()).isTrue();
    }
}
