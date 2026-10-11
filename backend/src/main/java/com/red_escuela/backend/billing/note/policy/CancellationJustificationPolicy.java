package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_JUSTIFICATION_REQUIRED"}, decisions = {"D-165", "D-127"})
public final class CancellationJustificationPolicy implements Policy<InvoiceCancellationContext> {

    private static final int MAX_JUSTIFICATION_LENGTH = 1000;

    @Override
    public String ruleName() {
        return "CancellationJustificationPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceCancellationContext context) {
        String justification = context.command().justification();
        if (justification == null || justification.isBlank() || justification.length() > MAX_JUSTIFICATION_LENGTH) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_JUSTIFICATION_REQUIRED", "La cancelación necesita una justificación de hasta 1000 caracteres");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
