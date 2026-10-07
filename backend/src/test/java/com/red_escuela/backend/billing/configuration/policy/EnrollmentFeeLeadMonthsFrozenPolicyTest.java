package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.update;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-201")
class EnrollmentFeeLeadMonthsFrozenPolicyTest {
    private final EnrollmentFeeLeadMonthsFrozenPolicy policy = new EnrollmentFeeLeadMonthsFrozenPolicy();

    private static ProgramYearBillingTemplateSnapshot withLead(int leadMonths) {
        return new ProgramYearBillingTemplateSnapshot(1, 2, null, 10, leadMonths);
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(update(withLead(1), withLead(2), Set.of(1), Set.of()));

        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("ENROLLMENT_FEE_LEAD_MONTHS_FROZEN");
        assertThat(EnrollmentFeeLeadMonthsFrozenPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsTheSameLeadWithIssuedFeesAndAnyLeadWithNoneIssued() {
        assertThat(policy.evaluate(update(withLead(1), withLead(1), Set.of(1), Set.of())).allowed()).isTrue();
        assertThat(policy.evaluate(update(withLead(1), withLead(3), Set.of(), Set.of())).allowed()).isTrue();
    }
}
