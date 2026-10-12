package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-147")
class SuspensionDaysNotNegativePolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 10);
    private final SuspensionDaysNotNegativePolicy policy = new SuspensionDaysNotNegativePolicy();

    private static LatePaymentSettingsValues values(int suspensionDays) {
        return new LatePaymentSettingsValues(0, Percentage.ofFraction(new BigDecimal("0.10")), suspensionDays, 1, TODAY, TODAY);
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluateRule(values(-1));

        assertThat(denial.allowed()).isFalse();
        assertThat(denial.code()).isEqualTo("NEGATIVE_SUSPENSION_DAYS");
        assertThat(SuspensionDaysNotNegativePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsZeroAndPositive() {
        assertThat(policy.evaluateRule(values(0)).allowed()).isTrue();
        assertThat(policy.evaluateRule(values(30)).allowed()).isTrue();
    }
}
