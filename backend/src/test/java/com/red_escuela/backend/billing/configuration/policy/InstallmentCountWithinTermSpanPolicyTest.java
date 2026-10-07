package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Set;

import static com.red_escuela.backend.billing.configuration.policy.YearChanges.*;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-034")
class InstallmentCountWithinTermSpanPolicyTest {
    private final InstallmentCountWithinTermSpanPolicy policy = new InstallmentCountWithinTermSpanPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(creation(snapshot(1, 2, null, 11)));
        assertThat(denial.allowed()).isFalse();
        assertThat(InstallmentCountWithinTermSpanPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsCountEqualToTheSpanAndBelow() {
        assertThat(policy.evaluate(creation(snapshot(1, 2, null, 10))).allowed()).isTrue();
        assertThat(policy.evaluate(creation(snapshot(1, 2, null, 1))).allowed()).isTrue();
    }

    @Test
    void skipsWhenTheTermHasNoDatesOrTheCountIsMissing() {
        assertThat(policy.evaluate(withoutDates(snapshot(1, 2, null, 50))).allowed()).isTrue();
        assertThat(policy.evaluate(creation(snapshot(1, 2, null, null))).allowed()).isTrue();
    }
}
