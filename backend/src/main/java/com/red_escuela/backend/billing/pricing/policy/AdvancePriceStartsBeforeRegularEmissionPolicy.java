package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_PRICE_STARTS_AFTER_REGULAR_EMISSION", decisions = {"D-532"})
public final class AdvancePriceStartsBeforeRegularEmissionPolicy implements Policy<AdvancePriceCheck> {
    public String ruleName() {
        return "AdvancePriceStartsBeforeRegularEmissionPolicy";
    }

    public PolicyVerdict evaluateRule(AdvancePriceCheck check) {
        boolean startsAfterEmission = check.regularEmissionStart().map(start -> check.effectiveFrom().isAfter(start)).orElse(false);
        return startsAfterEmission ? PolicyVerdict.deny(ruleName(), "ADVANCE_PRICE_STARTS_AFTER_REGULAR_EMISSION",
                "La vigencia del precio no puede ser posterior al mes en que se emite la matrícula del año lectivo.")
                : PolicyVerdict.allow(ruleName());
    }
}
