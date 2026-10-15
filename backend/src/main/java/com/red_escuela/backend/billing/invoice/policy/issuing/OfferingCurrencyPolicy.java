package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CURRENCY_MISMATCH", decisions = {"D-097"})
public final class OfferingCurrencyPolicy implements Policy<InvoiceIssuingContext> {

    @Override
    public String ruleName() {
        return "OfferingCurrencyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        boolean matches = context.facts().chargeAmount().value().currency() == context.facts().offeringCurrency();
        return matches ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CURRENCY_MISMATCH", "La moneda del monto no coincide con la de la oferta");
    }
}
