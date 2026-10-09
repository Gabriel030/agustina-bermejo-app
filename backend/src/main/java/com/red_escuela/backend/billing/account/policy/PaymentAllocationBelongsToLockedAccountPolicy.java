package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PAYMENT_ALLOCATION_ACCOUNT_MISMATCH", decisions = {"D-094", "D-097"})
public final class PaymentAllocationBelongsToLockedAccountPolicy implements Policy<AllocationReversalContext> {

    @Override
    public String ruleName() {
        return "PaymentAllocationBelongsToLockedAccountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AllocationReversalContext context) {
        return context.lockedAccountId() != null && context.lockedAccountId().equals(context.allocationAccountId())
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PAYMENT_ALLOCATION_ACCOUNT_MISMATCH",
                        "La imputación " + context.allocationId() + " no pertenece a la cuenta bloqueada " + context.lockedAccountId());
    }
}
