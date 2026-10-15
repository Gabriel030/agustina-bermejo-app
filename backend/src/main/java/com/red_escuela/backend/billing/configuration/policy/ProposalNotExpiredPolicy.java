package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PROPOSAL_EXPIRED", decisions = {"D-180"})
public final class ProposalNotExpiredPolicy implements Policy<ProposalApprovalCheck> {
    public String ruleName() {
        return "ProposalNotExpiredPolicy";
    }

    public PolicyVerdict evaluateRule(ProposalApprovalCheck approval) {
        boolean reachedStartWithoutDecision = !approval.effectiveFrom().isAfter(approval.today()) && approval.proposedOn().isBefore(approval.today());
        return !reachedStartWithoutDecision ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "PROPOSAL_EXPIRED", "La propuesta llegó a su fecha de inicio sin decisión y venció.");
    }
}
