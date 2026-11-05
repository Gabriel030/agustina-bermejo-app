package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PROPOSAL_ALREADY_OPEN", decisions = {"D-116", "D-211"})
public final class SingleOpenPriceProposalPolicy implements Policy<PriceProposalSlot> {
    public String ruleName() {
        return "SingleOpenPriceProposalPolicy";
    }

    public PolicyVerdict evaluateRule(PriceProposalSlot slot) {
        return !slot.openProposalExists() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PROPOSAL_ALREADY_OPEN", "Ya hay una propuesta pendiente sobre este precio.");
    }
}
