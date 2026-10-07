package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.issuing.IssuanceContext;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ENTRY_DISCOUNT_NOT_APPLICABLE", decisions = {"D-018"})
public final class EntryFeeDiscountPolicy implements Policy<IssuanceContext> {
    public String name() {
        return "EntryFeeDiscountPolicy";
    }

    public PolicyVerdict evaluate(IssuanceContext ctx) {
        boolean applies = ctx.request().entry() && ctx.request().reason() != IssueReason.ADMISSION
                && ctx.entryFeeDiscount() != null && ctx.entryFeeDiscount().fraction().signum() > 0;
        return applies ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "ENTRY_DISCOUNT_NOT_APPLICABLE", "No corresponde descuento de ingreso");
    }
}
