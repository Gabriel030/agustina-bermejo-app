package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PROPOSAL_ALREADY_OPEN", decisions = {"D-116"})
public final class SingleOpenProposalPerTopicPolicy implements Policy<ProposalSlot> {
    public String ruleName() {
        return "SingleOpenProposalPerTopicPolicy";
    }

    public PolicyVerdict evaluateRule(ProposalSlot slot) {
        return !slot.openProposalExists() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "PROPOSAL_ALREADY_OPEN", "Ya hay una propuesta pendiente sobre este tema.");
    }
}
