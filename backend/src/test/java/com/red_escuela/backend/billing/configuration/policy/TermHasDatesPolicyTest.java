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
class TermHasDatesPolicyTest {
    private final TermHasDatesPolicy policy = new TermHasDatesPolicy();

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(withoutDates(snapshot(1, 2, null, 10)));
        assertThat(denial.allowed()).isFalse();
        assertThat(TermHasDatesPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("TERM_WITHOUT_DATES");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsATermWithStartAndEnd() {
        assertThat(policy.evaluateRule(creation(snapshot(1, 2, null, 10))).allowed()).isTrue();
    }
}
