package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "SUSPENSION_INVOICE_COUNT_BELOW_ONE", decisions = {"D-147"})
public final class SuspensionInvoiceCountAtLeastOnePolicy implements Policy<LatePaymentSettingsValues> {
    public String ruleName() {
        return "SuspensionInvoiceCountAtLeastOnePolicy";
    }

    public PolicyVerdict evaluateRule(LatePaymentSettingsValues values) {
        return values.suspensionAfterInvoiceCount() >= 1 ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "SUSPENSION_INVOICE_COUNT_BELOW_ONE", "Las facturas vencidas para suspender deben ser al menos 1.");
    }
}
