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
class FirstEnrollmentFeeNotAfterTermStartPolicyTest {
    private final FirstEnrollmentFeeNotAfterTermStartPolicy policy = new FirstEnrollmentFeeNotAfterTermStartPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(creation(snapshot(1, 4, null, 10)));
        assertThat(denial.allowed()).isFalse();
        assertThat(FirstEnrollmentFeeNotAfterTermStartPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("FIRST_ENROLLMENT_FEE_AFTER_TERM_START");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsFirstFeeInTheSameMonthAsTermStartOrBefore() {
        assertThat(policy.evaluateRule(creation(snapshot(1, 3, null, 10))).allowed()).isTrue();
        assertThat(policy.evaluateRule(creation(snapshot(1, 2, null, 10))).allowed()).isTrue();
    }

    @Test
    void anAutumnMonthBelongsToThePreviousYearWhenSixOrMoreMonthsBeforeTermStart() {
        assertThat(policy.evaluateRule(creation(snapshot(1, 9, null, 10))).allowed()).isTrue();
        assertThat(policy.evaluateRule(creation(snapshot(1, 8, null, 10))).allowed()).isFalse();
    }

    @Test
    void skipsWhenTheTermHasNoDates() {
        assertThat(policy.evaluateRule(withoutDates(snapshot(1, 4, null, 10))).allowed()).isTrue();
    }
}
