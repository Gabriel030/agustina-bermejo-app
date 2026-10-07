package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENTRY_DISCOUNT_NOT_APPLICABLE", decisions = {"D-018"})
public final class EntryFeeDiscountPolicy implements Policy<InvoiceIssuingContext> {
    public String ruleName() {
        return "EntryFeeDiscountPolicy";
    }

    public PolicyVerdict evaluateRule(InvoiceIssuingContext ctx) {
        boolean applies = ctx.request().entry() && ctx.request().reason() != InvoiceIssuingReason.PROGRAM_ADMISSION
                && ctx.entryFeeDiscount() != null && ctx.entryFeeDiscount().fraction().signum() > 0;
        return applies ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "ENTRY_DISCOUNT_NOT_APPLICABLE", "No corresponde descuento de ingreso");
    }
}
