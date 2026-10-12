package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "NEGATIVE_MARKETING_DISCOUNT", decisions = {"D-240"})
public final class MarketingDiscountNotNegativePolicy implements Policy<PriceVersionDraft> {
    public String ruleName() {
        return "MarketingDiscountNotNegativePolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean negative = draft.discountAmount() != null && draft.discountAmount().isNegative();
        return negative ? PolicyVerdict.deny(ruleName(), "NEGATIVE_MARKETING_DISCOUNT", "El descuento de marketing no puede ser negativo.") : PolicyVerdict.allow(ruleName());
    }
}
