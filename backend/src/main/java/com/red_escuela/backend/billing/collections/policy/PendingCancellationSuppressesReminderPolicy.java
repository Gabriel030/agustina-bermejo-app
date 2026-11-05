package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PENDING_CANCELLATION_REQUEST", decisions = {"D-247", "D-354"})
public final class PendingCancellationSuppressesReminderPolicy implements Policy<ReminderInvoiceFact> {

    @Override
    public String ruleName() {
        return "PendingCancellationSuppressesReminderPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReminderInvoiceFact context) {
        return context.openCancellationRequest()
                ? PolicyVerdict.deny(ruleName(), "PENDING_CANCELLATION_REQUEST", "La factura tiene un pedido de cancelación o de nota de crédito abierto: no recibe avisos hasta que el director decida")
                : PolicyVerdict.allow(ruleName());
    }
}
