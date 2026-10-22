package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"LATE_FEE_UNDER_PAYMENT_REVIEW"}, decisions = {"D-153", "D-291", "D-400"})
public final class LateFeeDebitNoteNotUnderPaymentReviewPolicy implements Policy<LateFeeDebitNoteAnnulmentContext> {

    @Override
    public String ruleName() {
        return "LateFeeDebitNoteNotUnderPaymentReviewPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeDebitNoteAnnulmentContext context) {
        if (context.paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW) {
            return PolicyVerdict.deny(ruleName(), "LATE_FEE_UNDER_PAYMENT_REVIEW", "El recargo " + context.debitNoteNumber() + " (id " + context.debitNoteId()
                    + ") tiene un comprobante de transferencia pendiente de revisión: no se puede cancelar la cuota hasta que se decida");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
