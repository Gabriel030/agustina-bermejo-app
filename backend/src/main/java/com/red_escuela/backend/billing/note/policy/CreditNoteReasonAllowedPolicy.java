package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_CREDIT_NOTE_REASON"}, decisions = {"D-165", "D-238"})
public final class CreditNoteReasonAllowedPolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteReasonAllowedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        CreditNoteReason reason = context.command().reason();
        if (reason == CreditNoteReason.BILLING_ERROR || reason == CreditNoteReason.LATE_FEE_ANNULMENT) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "INVALID_CREDIT_NOTE_REASON", "El motivo " + reason + " no se puede usar para una nota de crédito pedida por el usuario");
    }
}
