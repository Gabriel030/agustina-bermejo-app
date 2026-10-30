package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-055")
@Tag("D-161")
class ManualIssuanceWithinCurrentMonthPolicyTest {
    private static final YearMonth CURRENT = YearMonth.of(2031, 10);
    private final ManualIssuanceWithinCurrentMonthPolicy policy = new ManualIssuanceWithinCurrentMonthPolicy();

    private PolicyVerdict evaluate(YearMonth requested) {
        return policy.evaluateRule(new ManualIssuanceWithinCurrentMonthContext(requested, CURRENT));
    }

    @Test
    void theCurrentMonthIsAllowed() {
        assertThat(evaluate(CURRENT).allowed()).isTrue();
    }

    @Test
    void thePreviousMonthIsDenied() {
        PolicyVerdict verdict = evaluate(CURRENT.minusMonths(1));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH");
    }

    @Test
    void theNextMonthIsDenied() {
        assertThat(evaluate(CURRENT.plusMonths(1)).code()).isEqualTo("MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH");
    }

    @Test
    void sameMonthOfAnotherYearIsDenied() {
        assertThat(evaluate(CURRENT.minusYears(1)).allowed()).isFalse();
    }
}
