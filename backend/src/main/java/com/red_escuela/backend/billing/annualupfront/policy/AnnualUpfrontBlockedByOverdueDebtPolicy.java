package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT"}, decisions = {"D-152", "D-292"})
public final class AnnualUpfrontBlockedByOverdueDebtPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontBlockedByOverdueDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.hasOverdueDebt()
                ? PolicyVerdict.deny(ruleName(), "OVERDUE_DEBT_BLOCKS_ANNUAL_UPFRONT", "Hay deuda vencida: regularizala para pagar el año completo.")
                : PolicyVerdict.allow(ruleName());
    }
}
