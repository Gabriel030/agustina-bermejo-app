package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PAYMENT_ALLOCATION_ALREADY_REVERSED", decisions = {"D-094"})
public final class PaymentAllocationReversedOnlyOncePolicy implements Policy<AllocationReversalContext> {

    @Override
    public String ruleName() {
        return "PaymentAllocationReversedOnlyOncePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AllocationReversalContext context) {
        return context.alreadyReversed()
                ? PolicyVerdict.deny(ruleName(), "PAYMENT_ALLOCATION_ALREADY_REVERSED", "La imputación " + context.allocationId() + " ya fue revertida")
                : PolicyVerdict.allow(ruleName());
    }
}
