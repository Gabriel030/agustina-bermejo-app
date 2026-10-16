package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = "PRICE_AMOUNT_TOO_LARGE", decisions = {"D-214"})
public final class PriceAmountWithinLimitPolicy implements Policy<PriceVersionDraft> {
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    public String ruleName() {
        return "PriceAmountWithinLimitPolicy";
    }

    public PolicyVerdict evaluateRule(PriceVersionDraft draft) {
        boolean baseFits = draft.baseAmount().amount().compareTo(MAX_AMOUNT) <= 0;
        boolean discountFits = draft.discountAmount() == null || draft.discountAmount().amount().compareTo(MAX_AMOUNT) <= 0;
        return baseFits && discountFits ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PRICE_AMOUNT_TOO_LARGE", "El monto supera el máximo permitido de 9.999.999.999,99.");
    }
}
