package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT"}, decisions = {"D-165"})
public final class CreditNoteLinesWithinCreditableAmountPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteLinesWithinCreditableAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        for (CreditNoteLineAmount line : context.command().lines()) {
            boolean exceeds = context.invoice().findLine(line.lineNumber())
                    .map(facts -> line.amount().compareTo(facts.creditableAmount().value().amount()) > 0)
                    .orElse(true);
            if (exceeds) {
                return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT", "El renglón " + line.lineNumber() + " no admite acreditar ese monto");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
