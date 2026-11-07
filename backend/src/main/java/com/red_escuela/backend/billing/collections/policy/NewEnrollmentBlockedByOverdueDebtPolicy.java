package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OVERDUE_DEBT_BLOCKS_NEW_ENROLLMENT"}, decisions = {"D-065", "D-292"})
public final class NewEnrollmentBlockedByOverdueDebtPolicy implements Policy<NewEnrollmentDebtCheck> {

    @Override
    public String ruleName() {
        return "NewEnrollmentBlockedByOverdueDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(NewEnrollmentDebtCheck check) {
        return check.blockNewEnrollmentsWhenOverdue() && check.hasOverdueDebt()
                ? PolicyVerdict.deny(ruleName(), "OVERDUE_DEBT_BLOCKS_NEW_ENROLLMENT",
                        "Hay deuda vencida en alguna inscripción: hay que regularizarla antes de inscribirse a algo nuevo")
                : PolicyVerdict.allow(ruleName());
    }
}
