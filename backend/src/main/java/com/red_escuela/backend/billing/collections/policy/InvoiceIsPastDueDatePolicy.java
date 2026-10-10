package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVOICE_NOT_PENDING", "INVOICE_WITHIN_GRACE_PERIOD"}, decisions = {"D-057", "D-153", "D-288"})
public final class InvoiceIsPastDueDatePolicy implements Policy<OverdueDetectionContext> {

    @Override
    public String ruleName() {
        return "InvoiceIsPastDueDatePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(OverdueDetectionContext context) {
        if (context.paymentStatus() != InvoicePaymentStatus.PENDING) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_NOT_PENDING", "Solo una factura pendiente puede pasar a vencida");
        }
        if (!context.today().isAfter(context.lastDayWithoutOverdue())) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_WITHIN_GRACE_PERIOD", "La factura todavía está dentro del vencimiento más la gracia");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
