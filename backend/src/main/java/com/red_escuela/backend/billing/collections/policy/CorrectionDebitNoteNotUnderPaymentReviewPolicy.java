package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "PENDING_BANK_TRANSFER_ON_INVOICE", decisions = {"D-153", "D-375", "D-378"})
public final class CorrectionDebitNoteNotUnderPaymentReviewPolicy implements Policy<LateFeeCorrectionContext> {

    @Override
    public String ruleName() {
        return "CorrectionDebitNoteNotUnderPaymentReviewPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeCorrectionContext context) {
        return context.paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW
                ? PolicyVerdict.deny(ruleName(), "PENDING_BANK_TRANSFER_ON_INVOICE", "La nota de débito tiene un comprobante pendiente de revisión: la corrección espera a que se decida")
                : PolicyVerdict.allow(ruleName());
    }
}
