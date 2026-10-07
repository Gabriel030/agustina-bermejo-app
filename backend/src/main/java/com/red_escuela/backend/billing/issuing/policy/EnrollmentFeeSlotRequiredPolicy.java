package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENROLLMENT_FEE_SLOT_REQUIRED", decisions = {"D-002", "D-009"})
public final class EnrollmentFeeSlotRequiredPolicy implements Policy<IssuanceContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotRequiredPolicy";
    }

    public PolicyVerdict evaluateRule(IssuanceContext ctx) {
        boolean programFee = ctx.request().feeType() == FeeType.ENROLLMENT_FEE
                && (ctx.request().target() instanceof ChargeTarget.ProgramStudent || ctx.request().target() instanceof ChargeTarget.ProgramApplicant);
        if (programFee && (ctx.request().templateId() == null || ctx.request().slot() == null)) {
            return PolicyVerdict.deny(name(), "ENROLLMENT_FEE_SLOT_REQUIRED", "La matrícula de carrera necesita plantilla y matrícula del ciclo");
        }
        return PolicyVerdict.allow(name());
    }
}
