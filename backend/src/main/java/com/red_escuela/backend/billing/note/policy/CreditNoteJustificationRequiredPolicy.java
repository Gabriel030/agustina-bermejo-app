package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_JUSTIFICATION_REQUIRED"}, decisions = {"D-165"})
public final class CreditNoteJustificationRequiredPolicy implements Policy<CreditNoteIssuingContext> {

    private static final int MAX_JUSTIFICATION_LENGTH = 1000;

    @Override
    public String ruleName() {
        return "CreditNoteJustificationRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        String justification = context.command().justification();
        if (justification == null || justification.isBlank() || justification.length() > MAX_JUSTIFICATION_LENGTH) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_JUSTIFICATION_REQUIRED", "La nota de crédito necesita una justificación de hasta 1000 caracteres");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
