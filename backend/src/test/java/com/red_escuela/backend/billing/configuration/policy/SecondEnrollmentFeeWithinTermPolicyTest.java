package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-002")
class SecondEnrollmentFeeWithinTermPolicyTest {
    private final SecondEnrollmentFeeWithinTermPolicy policy = new SecondEnrollmentFeeWithinTermPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(creation(snapshot(2, 2, 3, 10)));
        assertThat(denial.allowed()).isFalse();
        assertThat(SecondEnrollmentFeeWithinTermPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("SECOND_ENROLLMENT_FEE_OUTSIDE_TERM");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsSecondFeeAfterTermStartUpToTermEndMonthInclusive() {
        assertThat(policy.evaluate(creation(snapshot(2, 2, 4, 10))).allowed()).isTrue();
        assertThat(policy.evaluate(creation(snapshot(2, 2, 12, 10))).allowed()).isTrue();
    }

    @Test
    void deniesSecondFeeAfterTermEnd() {
        YearBillingConfigurationChange shortTerm = termRange(YearMonth.of(2027, 3), YearMonth.of(2027, 6), Set.of());
        YearBillingConfigurationChange change = new YearBillingConfigurationChange(shortTerm.current(), snapshot(2, 2, 7, 3),
                shortTerm.termStart(), shortTerm.termEnd(), Set.of(), Set.of());
        assertThat(policy.evaluate(change).allowed()).isFalse();
    }

    @Test
    void ignoresSingleEnrollmentAndTermsWithoutDates() {
        assertThat(policy.evaluate(creation(snapshot(1, 2, null, 10))).allowed()).isTrue();
        assertThat(policy.evaluate(withoutDates(snapshot(2, 2, 3, 10))).allowed()).isTrue();
    }
}
