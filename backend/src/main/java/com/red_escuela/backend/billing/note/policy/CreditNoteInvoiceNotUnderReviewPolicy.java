package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PENDING_BANK_TRANSFER_ON_INVOICE"}, decisions = {"D-153", "D-165"})
public final class CreditNoteInvoiceNotUnderReviewPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteInvoiceNotUnderReviewPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.invoice().paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW) {
            return PolicyVerdict.deny(ruleName(), "PENDING_BANK_TRANSFER_ON_INVOICE", "La factura tiene un comprobante de transferencia pendiente de revisión");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
