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
class GracePeriodWithinSuspensionDaysPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 10);
    private final GracePeriodWithinSuspensionDaysPolicy policy = new GracePeriodWithinSuspensionDaysPolicy();

    private static LatePaymentSettingsValues values(int grace, int suspensionDays, int invoiceCount, LocalDate effectiveFrom) {
        return new LatePaymentSettingsValues(grace, Percentage.ofFraction(new BigDecimal("0.10")), suspensionDays, invoiceCount, effectiveFrom, TODAY);
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(values(40, 30, 2, TODAY));
        assertThat(denial.allowed()).isFalse();
        assertThat(GracePeriodWithinSuspensionDaysPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("GRACE_PERIOD_EXCEEDS_SUSPENSION_DAYS");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsGraceEqualToSuspensionDays() {
        assertThat(policy.evaluate(values(30, 30, 2, TODAY)).allowed()).isTrue();
    }

    @Test
    void deniesGraceOneDayAboveSuspensionDays() {
        assertThat(policy.evaluate(values(31, 30, 2, TODAY)).allowed()).isFalse();
    }
}
