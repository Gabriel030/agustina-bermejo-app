package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CURRENCY_MISMATCH", decisions = {"D-097"})
public final class OfferingCurrencyPolicy implements Policy<IssuanceContext> {
    public String ruleName() {
        return "OfferingCurrencyPolicy";
    }

    public PolicyVerdict evaluateRule(IssuanceContext ctx) {
        boolean matches = ctx.amount() == null || ctx.amount().currency() == ctx.offeringCurrency();
        return matches ? PolicyVerdict.allow(name())
                : PolicyVerdict.deny(name(), "CURRENCY_MISMATCH", "La moneda del monto no coincide con la de la oferta");
    }
}
