package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CURRENCY_MISMATCH", decisions = {"D-097"})
public final class OfferingCurrencyPolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "OfferingCurrencyPolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        boolean matches = ctx.amount() == null || ctx.amount().currency() == ctx.offeringCurrency();
        return matches ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CURRENCY_MISMATCH", "La moneda del monto no coincide con la de la oferta");
    }
}
