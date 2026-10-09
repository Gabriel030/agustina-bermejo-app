package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = "MARKETING_DISCOUNT_PERCENTAGE_ABOVE_HUNDRED", decisions = {"D-240"})
public final class MarketingDiscountPercentageNotAboveHundredPolicy implements Policy<PriceVersionDraft> {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public String ruleName() {
        return "MarketingDiscountPercentageNotAboveHundredPolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean above = draft.hasDiscountOfType(MarketingDiscountType.PERCENTAGE) && draft.discountValue().compareTo(HUNDRED) > 0;
        return above ? PolicyVerdict.deny(ruleName(), "MARKETING_DISCOUNT_PERCENTAGE_ABOVE_HUNDRED", "El descuento de marketing no puede superar el 100 %.")
                : PolicyVerdict.allow(ruleName());
    }
}
