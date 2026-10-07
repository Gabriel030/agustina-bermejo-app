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
class SecondEnrollmentFeeAfterFirstPolicyTest {
    private final SecondEnrollmentFeeAfterFirstPolicy policy = new SecondEnrollmentFeeAfterFirstPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(creation(snapshot(2, 7, 7, 10)));
        assertThat(denial.allowed()).isFalse();
        assertThat(SecondEnrollmentFeeAfterFirstPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("SECOND_ENROLLMENT_FEE_NOT_AFTER_FIRST");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsSecondMonthStrictlyAfterFirstAndIgnoresSingleEnrollment() {
        assertThat(policy.evaluate(creation(snapshot(2, 2, 3, 10))).allowed()).isTrue();
        assertThat(policy.evaluate(creation(snapshot(1, 7, null, 10))).allowed()).isTrue();
    }

    @Test
    void deniesMissingSecondMonthAndEarlierSecondMonth() {
        assertThat(policy.evaluate(creation(snapshot(2, 2, null, 10))).allowed()).isFalse();
        assertThat(policy.evaluate(creation(snapshot(2, 7, 2, 10))).allowed()).isFalse();
    }
}
