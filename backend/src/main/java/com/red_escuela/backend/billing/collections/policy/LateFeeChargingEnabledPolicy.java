package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "LATE_FEE_CHARGING_DISABLED", decisions = {"D-210", "D-294"})
public final class LateFeeChargingEnabledPolicy implements Policy<LateFeeIssuingContext> {

    @Override
    public String ruleName() {
        return "LateFeeChargingEnabledPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeIssuingContext context) {
        return context.factDateTerms().lateFeeChargingEnabled()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "LATE_FEE_CHARGING_DISABLED", "El cobro de recargos no estaba habilitado en la fecha del hecho");
    }
}
