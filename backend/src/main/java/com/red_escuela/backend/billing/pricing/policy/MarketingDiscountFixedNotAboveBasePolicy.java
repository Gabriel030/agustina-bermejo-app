package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "MARKETING_DISCOUNT_EXCEEDS_BASE", decisions = {"D-240"})
public final class MarketingDiscountFixedNotAboveBasePolicy implements Policy<PriceVersionDraft> {
    public String ruleName() {
        return "MarketingDiscountFixedNotAboveBasePolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean above = draft.hasDiscountOfType(MarketingDiscountType.FIXED) && draft.discountValue().compareTo(draft.baseAmount()) > 0;
        return above ? PolicyVerdict.deny(ruleName(), "MARKETING_DISCOUNT_EXCEEDS_BASE", "El descuento de marketing no puede superar el precio base.")
                : PolicyVerdict.allow(ruleName());
    }
}
