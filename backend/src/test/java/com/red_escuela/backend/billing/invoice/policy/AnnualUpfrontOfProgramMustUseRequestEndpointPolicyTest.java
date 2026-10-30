package com.red_escuela.backend.billing.invoice.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnnualUpfrontOfProgramMustUseRequestEndpointPolicyTest {
    private final AnnualUpfrontOfProgramMustUseRequestEndpointPolicy policy = new AnnualUpfrontOfProgramMustUseRequestEndpointPolicy();

    @Test
    @Tag("D-131")
    @Tag("D-444")
    void annualUpfrontOfAProgramIsRejectedWithItsViolationCode() {
        var verdict = policy.evaluateRule(new PaymentPlanChoice(true, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.reason()).contains("pedido del pago anual");
        assertThat(verdict.code()).isEqualTo("ANNUAL_UPFRONT_USE_REQUEST_ENDPOINT");
        assertThat(verdict.policy()).isEqualTo("AnnualUpfrontOfProgramMustUseRequestEndpointPolicy");
    }

    @Test
    @Tag("D-131")
    @Tag("D-444")
    void monthlyPlanOfAProgramIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(false, true)).allowed()).isTrue();
    }

    @Test
    @Tag("D-039")
    @Tag("D-444")
    void annualUpfrontOfACourseIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(true, false)).allowed()).isTrue();
    }

    @Test
    @Tag("D-444")
    void monthlyPlanOfACourseIsAllowed() {
        assertThat(policy.evaluateRule(new PaymentPlanChoice(false, false)).allowed()).isTrue();
    }
}
