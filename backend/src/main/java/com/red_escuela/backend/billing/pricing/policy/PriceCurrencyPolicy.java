package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscountType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "CURRENCY_MISMATCH", decisions = {"D-097", "D-240"})
public final class PriceCurrencyPolicy implements Policy<PriceVersionDraft> {
    public String ruleName() {
        return "PriceCurrencyPolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean baseMatches = draft.baseCurrency() == draft.priceCurrency();
        boolean discountMatches = !draft.hasDiscountOfType(MarketingDiscountType.FIXED) || draft.discountCurrency() == draft.baseCurrency();
        return baseMatches && discountMatches ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CURRENCY_MISMATCH", "El precio y el descuento deben estar en la moneda de la oferta.");
    }
}
