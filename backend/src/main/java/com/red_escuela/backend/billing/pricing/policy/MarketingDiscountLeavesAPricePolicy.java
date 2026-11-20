package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = "MARKETING_DISCOUNT_LEAVES_NO_PRICE", decisions = {"D-536"})
public final class MarketingDiscountLeavesAPricePolicy implements Policy<PriceVersionDraft> {
    public String ruleName() {
        return "MarketingDiscountLeavesAPricePolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean percentageCoversAll = draft.hasDiscountOfType(MarketingDiscountType.PERCENTAGE) && draft.discountPercentage().fraction().compareTo(BigDecimal.ONE) >= 0;
        boolean fixedEqualsBase = draft.hasDiscountOfType(MarketingDiscountType.FIXED) && draft.discountAmount().currency() == draft.baseAmount().currency()
                && draft.baseAmount().amount().signum() > 0 && draft.discountAmount().amount().compareTo(draft.baseAmount().amount()) == 0;
        return percentageCoversAll || fixedEqualsBase
                ? PolicyVerdict.deny(ruleName(), "MARKETING_DISCOUNT_LEAVES_NO_PRICE",
                        "El descuento de marketing no puede dejar el precio en cero. Para ofrecerlo gratis, usá una beca o un descuento de alumno.")
                : PolicyVerdict.allow(ruleName());
    }
}
