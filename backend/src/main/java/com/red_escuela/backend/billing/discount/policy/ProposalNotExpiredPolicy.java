package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"STUDENT_DISCOUNT_PROPOSAL_EXPIRED"}, decisions = {"D-238", "D-180"})
public final class ProposalNotExpiredPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "ProposalNotExpiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        boolean expired = check.approvalDeadline() != null && check.today() != null && !check.today().isBefore(check.approvalDeadline());
        return expired
                ? PolicyVerdict.deny(ruleName(), "STUDENT_DISCOUNT_PROPOSAL_EXPIRED", "La propuesta vence el " + check.approvalDeadline() + ", primer día del período de inicio.")
                : PolicyVerdict.allow(ruleName());
    }
}
