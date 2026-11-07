package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-152")
@Tag("D-292")
class ExamFeeBlockedByOverdueDebtPolicyTest {

    private final ExamFeeBlockedByOverdueDebtPolicy policy = new ExamFeeBlockedByOverdueDebtPolicy();

    @Test
    void overdueDebtBlocksTheExamFee() {
        PolicyVerdict verdict = policy.evaluateRule(new ExamFeeIssuanceCheck(1L, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("OVERDUE_DEBT_BLOCKS_EXAM_FEE");
    }

    @Test
    void withoutOverdueDebtTheExamFeeIsAllowed() {
        assertThat(policy.evaluateRule(new ExamFeeIssuanceCheck(1L, false)).allowed()).isTrue();
    }
}
