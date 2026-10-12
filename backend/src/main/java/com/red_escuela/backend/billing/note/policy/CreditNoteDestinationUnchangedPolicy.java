package com.red_escuela.backend.billing.note.policy;


import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_DISTRIBUTION_MISMATCH"}, decisions = {"D-165"})
public final class CreditNoteDestinationUnchangedPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteDestinationUnchangedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.command().expectedDestination() != context.invoice().currentDestination()) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_DISTRIBUTION_MISMATCH", "La factura cambió de estado de cobro desde que se pidió la nota de crédito");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
