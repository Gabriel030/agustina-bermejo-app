package com.red_escuela.backend.billing.pricing.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_PRICE_MUST_START_IN_THE_FUTURE", decisions = {"D-532"})
public final class AdvancePriceStartsInTheFuturePolicy implements Policy<AdvancePriceCheck> {
    public String ruleName() {
        return "AdvancePriceStartsInTheFuturePolicy";
    }

    public PolicyVerdict evaluateRule(AdvancePriceCheck check) {
        return check.effectiveFrom().isAfter(check.today()) ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_PRICE_MUST_START_IN_THE_FUTURE",
                "La versión de precio de un año lectivo debe empezar en una fecha futura: mientras tanto no reemplaza al precio vigente.");
    }
}
