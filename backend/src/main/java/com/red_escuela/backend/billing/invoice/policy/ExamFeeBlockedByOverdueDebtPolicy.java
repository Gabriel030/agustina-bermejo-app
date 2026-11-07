package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OVERDUE_DEBT_BLOCKS_EXAM_FEE"}, decisions = {"D-152", "D-292"})
public final class ExamFeeBlockedByOverdueDebtPolicy implements Policy<ExamFeeIssuanceCheck> {

    @Override
    public String ruleName() {
        return "ExamFeeBlockedByOverdueDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ExamFeeIssuanceCheck check) {
        return check.hasOverdueDebt()
                ? PolicyVerdict.deny(ruleName(), "OVERDUE_DEBT_BLOCKS_EXAM_FEE", "Hay deuda vencida: regularizala para poder emitir un derecho de examen.")
                : PolicyVerdict.allow(ruleName());
    }
}
