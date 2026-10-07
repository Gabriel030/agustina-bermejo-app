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
class SuspensionInvoiceCountAtLeastOnePolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 4, 10);
    private final SuspensionInvoiceCountAtLeastOnePolicy policy = new SuspensionInvoiceCountAtLeastOnePolicy();

    private static LatePaymentSettingsValues values(int grace, int suspensionDays, int invoiceCount, LocalDate effectiveFrom) {
        return new LatePaymentSettingsValues(grace, Percentage.ofFraction(new BigDecimal("0.10")), suspensionDays, invoiceCount, effectiveFrom, TODAY);
    }

    @Test
    void everyReturnedCodeIsDeclaredInThePolicyRule() {
        PolicyVerdict denial = policy.evaluate(values(5, 30, 0, TODAY));
        assertThat(denial.allowed()).isFalse();
        assertThat(SuspensionInvoiceCountAtLeastOnePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(denial.code());
        assertThat(denial.code()).isEqualTo("SUSPENSION_INVOICE_COUNT_BELOW_ONE");
        assertThat(denial.reason()).isNotBlank();
    }

    @Test
    void allowsExactlyOne() {
        assertThat(policy.evaluate(values(5, 30, 1, TODAY)).allowed()).isTrue();
    }
}
