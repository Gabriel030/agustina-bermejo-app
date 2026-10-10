package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED"}, decisions = {"D-165", "D-229", "D-300", "D-242"})
public final class SystemInitiatedCancellationPolicy implements Policy<InvoiceCancellationContext> {

    @Override
    public String ruleName() {
        return "SystemInitiatedCancellationPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceCancellationContext context) {
        CancelInvoiceCommand command = context.command();
        boolean processActor = command.requestedByPersonId() == null;
        if (command.initiatedBySystem() == processActor) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED",
                "Una cancelación sin director solo la puede iniciar un proceso del sistema con un origen autorizado");
    }
}
