package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ALREADY_ISSUED", "FEE_CANCELLED", "ADVANCE_FEE_EXPIRED"}, decisions = {"D-009", "D-534"})
public final class EnrollmentFeeSlotNotAlreadyIssuedPolicy implements InvoicePlanningPolicy<EnrollmentFeeSlotPlanningContext> {
    public String ruleName() {
        return "EnrollmentFeeSlotNotAlreadyIssuedPolicy";
    }

    public int plannerQuestionNumber() {
        return 7;
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeSlotPlanningContext context) {
        if (context.issuedFee().isPresent()) {
            return PolicyVerdict.deny(ruleName(), context.issuedFee().orElseThrow().isCancelled() ? "FEE_CANCELLED" : "ALREADY_ISSUED", context.slotLabel());
        }
        return context.advanceFeeExpired() ? PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_EXPIRED", context.slotLabel()) : PolicyVerdict.allow(ruleName());
    }
}
