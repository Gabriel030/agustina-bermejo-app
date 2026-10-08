package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ENROLLMENT_FEE_NOT_ISSUED", "ENROLLMENT_FEE_NOT_PAID_FOR_TERM"}, decisions = {"D-166", "D-010"})
public final class EnrollmentFeeSettledPolicy implements Policy<EnrollmentFeeRequirementContext> {
    public String ruleName() {
        return "EnrollmentFeeSettledPolicy";
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeRequirementContext context) {
        return context.issuedFee().map(this::verdictForIssuedFee)
                .orElseGet(() -> PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_NOT_ISSUED", "la matrícula todavía no fue emitida"));
    }

    private PolicyVerdict verdictForIssuedFee(IssuedEnrollmentFee fee) {
        return fee.isSettled() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ENROLLMENT_FEE_NOT_PAID_FOR_TERM", "la matrícula emitida no está paga");
    }
}
