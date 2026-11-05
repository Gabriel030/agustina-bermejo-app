package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NO_EFFECTIVE_PRICE_FOR_ANNUAL_UPFRONT"}, decisions = {"D-439"})
public final class AnnualUpfrontPriceAvailablePolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontPriceAvailablePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return !check.priceAvailable()
                ? PolicyVerdict.deny(ruleName(), "NO_EFFECTIVE_PRICE_FOR_ANNUAL_UPFRONT", "No hay un precio de cuota vigente para armar el pago anual.")
                : PolicyVerdict.allow(ruleName());
    }
}
