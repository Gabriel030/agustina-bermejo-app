package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_LINES_INVALID"}, decisions = {"D-165"})
public final class CreditNoteLinesBelongToInvoicePolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteLinesBelongToInvoicePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        List<CreditNoteLineAmount> lines = context.command().lines();
        if (lines.isEmpty()) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_LINES_INVALID", "La nota de crédito necesita al menos un renglón");
        }
        Set<Integer> seen = new HashSet<>();
        for (CreditNoteLineAmount line : lines) {
            if (line.amount().amount().signum() <= 0) {
                return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_LINES_INVALID", "El monto del renglón " + line.lineNumber() + " debe ser mayor que cero");
            }
            if (!seen.add(line.lineNumber()) || context.invoice().findLine(line.lineNumber()).map(facts -> facts.creditableAmount().value().currency() != line.amount().currency()).orElse(true)) {
                return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_LINES_INVALID", "El renglón " + line.lineNumber() + " está repetido o no pertenece a la factura");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
