package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PENDING_BANK_TRANSFER_ON_INVOICE"}, decisions = {"D-153", "D-127"})
public final class CancellationInvoiceNotUnderReviewPolicy implements Policy<InvoiceCancellationContext> {

    @Override
    public String ruleName() {
        return "CancellationInvoiceNotUnderReviewPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceCancellationContext context) {
        if (context.invoice().paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW) {
            return PolicyVerdict.deny(ruleName(), "PENDING_BANK_TRANSFER_ON_INVOICE", "La factura tiene un comprobante de transferencia pendiente de revisión");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
