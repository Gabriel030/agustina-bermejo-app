package com.red_escuela.backend.billing.note.policy;


import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CREDIT_NOTE_RECIPIENT_MISMATCH"}, decisions = {"D-244"})
public final class CreditNoteRecipientMatchesInvoicePolicy implements Policy<CreditNoteIssuingContext> {

    @Override
    public String ruleName() {
        return "CreditNoteRecipientMatchesInvoicePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CreditNoteIssuingContext context) {
        String invoiceRecipient = context.invoice().recipientDocumentNumber();
        if (invoiceRecipient == null || !invoiceRecipient.equals(context.creditNoteRecipientDocumentNumber())) {
            return PolicyVerdict.deny(ruleName(), "CREDIT_NOTE_RECIPIENT_MISMATCH", "La nota de crédito debe ir al mismo receptor que la factura que ajusta");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
