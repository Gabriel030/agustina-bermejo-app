package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.WithdrawalLineAmount;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.HashSet;
import java.util.Set;

@PolicyRule(violationCodes = {"WITHDRAWAL_REDUCTION_INVOICE_CHANGED"}, decisions = {"D-204", "D-248", "D-253"})
public final class WithdrawalReductionLinesUnchangedPolicy implements Policy<WithdrawalLineReductionContext> {

    @Override
    public String ruleName() {
        return "WithdrawalReductionLinesUnchangedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(WithdrawalLineReductionContext context) {
        Set<Integer> seen = new HashSet<>();
        for (WithdrawalLineAmount expected : context.item().lines()) {
            boolean unchanged = seen.add(expected.lineNumber()) && expected.amount().amount().signum() > 0
                    && context.invoice().findLine(expected.lineNumber())
                    .map(line -> line.creditableAmount().value().currency() == expected.amount().currency()
                            && line.creditableAmount().value().amount().compareTo(expected.amount().amount()) == 0)
                    .orElse(false);
            if (!unchanged) {
                return PolicyVerdict.deny(ruleName(), "WITHDRAWAL_REDUCTION_INVOICE_CHANGED", "El renglón " + expected.lineNumber() + " cambió desde que se pidió la cancelación");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
