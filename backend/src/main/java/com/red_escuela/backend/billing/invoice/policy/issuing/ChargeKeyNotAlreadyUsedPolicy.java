package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ALREADY_ISSUED", decisions = {"D-009", "D-021"})
public final class ChargeKeyNotAlreadyUsedPolicy implements Policy<InvoiceIssuingContext> {

    @Override
    public String ruleName() {
        return "ChargeKeyNotAlreadyUsedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceIssuingContext context) {
        return !context.chargeKeyAlreadyUsed()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ALREADY_ISSUED", "El cargo ya fue emitido");
    }
}
