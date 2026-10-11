package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ORIGIN_UNDER_PAYMENT_REVIEW", decisions = {"D-153", "D-154"})
public final class OriginNotUnderPaymentReviewPolicy implements Policy<LateFeeIssuingContext> {

    @Override
    public String ruleName() {
        return "OriginNotUnderPaymentReviewPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeIssuingContext context) {
        return context.origin().paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW
                ? PolicyVerdict.deny(ruleName(), "ORIGIN_UNDER_PAYMENT_REVIEW", "La factura está en revisión de pago: no recibe recargo mientras siga ahí")
                : PolicyVerdict.allow(ruleName());
    }
}
