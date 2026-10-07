package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_SLOT_NOT_CONFIGURED", decisions = {"D-009", "D-036"})
public final class EnrollmentFeeSlotWithinTemplateCountPolicy implements Policy<IssuanceContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotWithinTemplateCountPolicy";
    }

    public PolicyVerdict evaluateRule(IssuanceContext ctx) {
        Integer configuredCount = ctx.templateEnrollmentFeeCount();
        Integer slot = ctx.request().slot();
        boolean outsideConfiguration = configuredCount != null && slot != null && slot > configuredCount;
        return outsideConfiguration
                ? PolicyVerdict.deny(name(), "ENROLLMENT_FEE_SLOT_NOT_CONFIGURED", "La matrícula del ciclo ya no está configurada en la plantilla del año lectivo")
                : PolicyVerdict.allow(name());
    }
}
