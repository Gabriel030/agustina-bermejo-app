package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ALREADY_ISSUED", "FEE_CANCELLED"}, decisions = {"D-009"})
public final class EnrollmentFeeSlotNotAlreadyIssuedPolicy implements InvoicePlanningPolicy<EnrollmentFeeSlotPlanningContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotNotAlreadyIssuedPolicy";
    }

    public int plannerQuestionNumber() {
        return 7;
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeSlotPlanningContext context) {
        return context.issuedFee().isEmpty() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), context.issuedFee().orElseThrow().isCancelled() ? "FEE_CANCELLED" : "ALREADY_ISSUED", context.slotLabel());
    }
}
