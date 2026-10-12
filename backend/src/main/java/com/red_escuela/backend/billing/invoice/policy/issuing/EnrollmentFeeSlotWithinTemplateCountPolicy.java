package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_SLOT_NOT_CONFIGURED", decisions = {"D-009", "D-036"})
public final class EnrollmentFeeSlotWithinTemplateCountPolicy implements Policy<InvoiceIssuingContext> {

    @Override
    public String ruleName() {
        return "EnrollmentFeeSlotWithinTemplateCountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        Integer configuredCount = context.facts().templateEnrollmentFeeCount();
        Integer slot = context.request().enrollmentFeeSlot();
        boolean outsideConfiguration = configuredCount != null && slot != null && slot > configuredCount;
        return outsideConfiguration
                ? PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_SLOT_NOT_CONFIGURED", "La matrícula del ciclo ya no está configurada en la plantilla del año lectivo")
                : PolicyVerdict.allow(ruleName());
    }
}
