package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "LATE_FEE_FROZEN", decisions = {"D-148", "D-293"})
public final class LateFeeNotFrozenPolicy implements Policy<LateFeeIssuingContext> {

    @Override
    public String ruleName() {
        return "LateFeeNotFrozenPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeIssuingContext context) {
        boolean frozen = context.cutoffTerms().freezeLateFeesOnWithdrawal()
                && context.enrollmentEndDate().map(endDate -> context.period().period().firstDay().isAfter(endDate)).orElse(false);
        return frozen ? PolicyVerdict.deny(ruleName(), "LATE_FEE_FROZEN", "La inscripción terminó antes del período: el recargo está congelado")
                : PolicyVerdict.allow(ruleName());
    }
}
