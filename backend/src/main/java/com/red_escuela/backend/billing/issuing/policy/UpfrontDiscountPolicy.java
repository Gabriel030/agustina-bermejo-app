package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "UPFRONT_DISCOUNT_NOT_APPLICABLE", decisions = {"D-044"})
public final class UpfrontDiscountPolicy implements Policy<IssuanceContext> {
    public String name() {
        return "UpfrontDiscountPolicy";
    }

    public PolicyVerdict evaluate(IssuanceContext ctx) {
        boolean applies = ctx.request().reason() == IssueReason.UPFRONT && ctx.upfrontDiscount() != null;
        return applies ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "UPFRONT_DISCOUNT_NOT_APPLICABLE", "No corresponde descuento por pago anual");
    }
}
