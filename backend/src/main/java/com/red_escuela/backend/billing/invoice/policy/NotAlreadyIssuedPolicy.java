package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ALREADY_ISSUED", decisions = {"D-009"})
public final class NotAlreadyIssuedPolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "NotAlreadyIssuedPolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        return !ctx.alreadyIssued() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "ALREADY_ISSUED", "El cargo ya fue emitido");
    }
}
