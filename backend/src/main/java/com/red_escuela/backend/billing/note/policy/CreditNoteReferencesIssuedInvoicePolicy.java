package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_INVOICE_REFERENCE_REQUIRED"}, decisions = {"D-244"})
public final class CreditNoteReferencesIssuedInvoicePolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteReferencesIssuedInvoicePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        if (context.invoice() == null || context.invoice().documentStatus() != InvoiceDocumentStatus.ISSUED) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_INVOICE_REFERENCE_REQUIRED", "La nota de crédito debe referenciar una factura emitida");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
