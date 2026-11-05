package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"STUDENT_DISCOUNT_PROPOSAL_EXPIRED"}, decisions = {"D-389"})
public final class RevocationProposalNotExpiredPolicy implements Policy<StudentDiscountRevocationCheck> {

    @Override
    public String ruleName() {
        return "RevocationProposalNotExpiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountRevocationCheck check) {
        boolean expired = check.approvalDeadline() != null && check.today() != null && !check.today().isBefore(check.approvalDeadline());
        return expired
                ? PolicyVerdict.deny(ruleName(), "STUDENT_DISCOUNT_PROPOSAL_EXPIRED", "La propuesta de revocación venció el " + check.approvalDeadline().minusDays(1) + ", fin de mes.")
                : PolicyVerdict.allow(ruleName());
    }
}
