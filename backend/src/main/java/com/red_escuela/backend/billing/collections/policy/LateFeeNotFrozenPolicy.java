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
        long unfrozenDays = context.period().period().firstDay().datesUntil(context.period().period().lastDay().plusDays(1))
                .filter(day -> !context.frozenOn().test(day)).count();
        boolean frozen = unfrozenDays == 0 || context.period().chargedDays() > unfrozenDays;
        return frozen ? PolicyVerdict.deny(ruleName(), "LATE_FEE_FROZEN", "La inscripción terminó y el recargo estaba congelado en los días del período")
                : PolicyVerdict.allow(ruleName());
    }
}
