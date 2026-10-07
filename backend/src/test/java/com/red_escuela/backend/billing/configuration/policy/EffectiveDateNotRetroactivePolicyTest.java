package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-059")
class EffectiveDateNotRetroactivePolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 10);
    private final EffectiveDateNotRetroactivePolicy policy = new EffectiveDateNotRetroactivePolicy();

    private static LatePaymentSettingsValues values(int grace, int suspensionDays, int invoiceCount, LocalDate effectiveFrom) {
        return new LatePaymentSettingsValues(grace, Percentage.ofFraction(new BigDecimal("0.10")), suspensionDays, invoiceCount, effectiveFrom, TODAY);
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(values(5, 30, 2, TODAY.minusDays(1)));
        assertThat(denial.allowed()).isFalse();
        assertThat(EffectiveDateNotRetroactivePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("RETROACTIVE_EFFECTIVE_DATE");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsTodayAndFuture() {
        assertThat(policy.evaluate(values(5, 30, 2, TODAY)).allowed()).isTrue();
        assertThat(policy.evaluate(values(5, 30, 2, TODAY.plusDays(1))).allowed()).isTrue();
    }
}
