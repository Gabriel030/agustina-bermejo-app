package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-036")
class EnrollmentFeeMonthFrozenPolicyTest {
    private final EnrollmentFeeMonthFrozenPolicy policy = new EnrollmentFeeMonthFrozenPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(update(snapshot(2, 2, 7, 10), snapshot(2, 3, 7, 10), Set.of(1), Set.of()));
        assertThat(denial.allowed()).isFalse();
        assertThat(EnrollmentFeeMonthFrozenPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("ENROLLMENT_FEE_MONTH_FROZEN");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsChangingTheMonthOfASlotNotYetIssued() {
        assertThat(policy.evaluate(update(snapshot(2, 2, 7, 10), snapshot(2, 2, 8, 10), Set.of(1), Set.of())).allowed()).isTrue();
    }

    @Test
    void deniesChangingTheMonthOfAnIssuedSecondSlot() {
        assertThat(policy.evaluate(update(snapshot(2, 2, 7, 10), snapshot(2, 2, 8, 10), Set.of(1, 2), Set.of())).allowed()).isFalse();
    }

    @Test
    void doesNotDuplicateTheCountViolationWhenTheSlotDisappears() {
        assertThat(policy.evaluate(update(snapshot(2, 2, 7, 10), snapshot(1, 2, null, 10), Set.of(1, 2), Set.of())).allowed()).isTrue();
    }
}
