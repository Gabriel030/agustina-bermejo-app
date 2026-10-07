package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "UPFRONT_DISCOUNT_NOT_APPLICABLE", decisions = {"D-044"})
public final class UpfrontDiscountPolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "UpfrontDiscountPolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        boolean applies = ctx.request().reason() == InvoiceIssuingReason.UPFRONT && ctx.upfrontDiscount() != null;
        return applies ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "UPFRONT_DISCOUNT_NOT_APPLICABLE", "No corresponde descuento por pago anual");
    }
}
