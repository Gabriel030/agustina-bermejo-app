package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

public final class NotAlreadyIssuedPolicy implements Policy<IssuanceContext> {
    public String name() {
        return "NotAlreadyIssuedPolicy";
    }

    public PolicyVerdict evaluate(IssuanceContext ctx) {
        return !ctx.alreadyIssued() ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "ALREADY_ISSUED", "El cargo ya fue emitido");
    }
}
