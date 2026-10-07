package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-009")
@Tag("D-036")
class EnrollmentFeeSlotWithinTemplateCountPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 7, 10);
    private final EnrollmentFeeSlotWithinTemplateCountPolicy policy = new EnrollmentFeeSlotWithinTemplateCountPolicy();

    private static IssuanceContext context(Integer slot, Integer configuredCount) {
        ChargeRequest request = new ChargeRequest(new ChargeTarget.ProgramApplicant(null, null), FeeType.ENROLLMENT_FEE, YearMonth.from(TODAY), TODAY,
                IssueReason.ADMISSION, null, 5L, slot, false, null, null, null, null);
        return new IssuanceContext(request, TODAY, Currency.ARS, ars(1), null, null, null, null, configuredCount);
    }

    @Test
    void deniesASlotBeyondTheCountConfiguredInTheTemplate() {
        var denial = policy.evaluateRule(context(2, 1));

        assertThat(denial.allowed()).isFalse();
        assertThat(policy.getClass().getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
    }

    @Test
    void allowsASlotEqualToTheConfiguredCountAndWhenNothingIsKnown() {
        assertThat(policy.evaluateRule(context(2, 2)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(1, 2)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(2, null)).allowed()).isTrue();
        assertThat(policy.evaluateRule(context(null, 1)).allowed()).isTrue();
    }
}
