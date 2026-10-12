package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVALID_CREDIT_NOTE_REASON"}, decisions = {"D-127", "D-238"})
public final class CancellationReasonPolicy implements Policy<InvoiceCancellationContext> {

    @Override
    public String ruleName() {
        return "CancellationReasonPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceCancellationContext context) {
        CreditNoteReason reason = context.command().reason();
        if (reason == CreditNoteReason.CANCELLATION || reason == CreditNoteReason.BILLING_ERROR || reason == CreditNoteReason.LATE_FEE_ANNULMENT) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "INVALID_CREDIT_NOTE_REASON", "El motivo " + reason + " no se puede usar para cancelar una factura");
    }
}
