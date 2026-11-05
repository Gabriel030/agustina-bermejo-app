package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.math.BigDecimal;

@PolicyRule(violationCodes = {"WITHDRAWAL_REDUCTION_WOULD_CLEAR_DEBT"}, decisions = {"D-248", "D-142", "D-127"})
public final class WithdrawalReductionLeavesDebtPolicy implements Policy<WithdrawalLineReductionContext> {

    @Override
    public String ruleName() {
        return "WithdrawalReductionLeavesDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(WithdrawalLineReductionContext context) {
        BigDecimal reduced = context.item().lines().stream().map(line -> line.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (reduced.compareTo(context.invoice().outstandingAmount().value().amount()) < 0) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "WITHDRAWAL_REDUCTION_WOULD_CLEAR_DEBT", "La reducción por la baja debe dejar deuda de los meses cursados; si no, se cancela la factura");
    }
}
