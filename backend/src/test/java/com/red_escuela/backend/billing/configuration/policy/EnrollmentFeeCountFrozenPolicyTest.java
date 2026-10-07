package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-009")
@Tag("D-036")
class EnrollmentFeeCountFrozenPolicyTest {
    private final EnrollmentFeeCountFrozenPolicy policy = new EnrollmentFeeCountFrozenPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(update(snapshot(2, 2, 7, 10), snapshot(1, 2, null, 10), Set.of(1), Set.of()));
        assertThat(denial.allowed()).isFalse();
        assertThat(EnrollmentFeeCountFrozenPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("ENROLLMENT_FEE_COUNT_FROZEN");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsTheSameCountWithIssuedFeesAndAnyCountWithNoneIssued() {
        assertThat(policy.evaluateRule(update(snapshot(2, 2, 7, 10), snapshot(2, 2, 8, 10), Set.of(1), Set.of())).allowed()).isTrue();
        assertThat(policy.evaluateRule(update(snapshot(2, 2, 7, 10), snapshot(1, 2, null, 10), Set.of(), Set.of())).allowed()).isTrue();
    }
}
