package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptProtectionFacts;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INVOICE_ALREADY_OVERDUE", "RECEIPT_UPLOADED_AFTER_DUE_DATE", "TRANSFER_AFTER_DUE_DATE"}, decisions = {"D-154", "D-186", "D-153"})
public final class ReceiptUploadedByDueDatePolicy implements Policy<ReceiptProtectionCheck> {

    @Override
    public String ruleName() {
        return "ReceiptUploadedByDueDatePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReceiptProtectionCheck check) {
        ReceiptProtectionFacts facts = check.facts();
        if (facts.paymentStatus() != InvoicePaymentStatus.PENDING) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_ALREADY_OVERDUE", "La factura ya está vencida: el comprobante no la protege del recargo.");
        }
        if (facts.uploadDate().isAfter(facts.dueDate())) {
            return PolicyVerdict.deny(ruleName(), "RECEIPT_UPLOADED_AFTER_DUE_DATE", "El comprobante se subió después del vencimiento.");
        }
        if (facts.transferDate().isAfter(facts.dueDate())) {
            return PolicyVerdict.deny(ruleName(), "TRANSFER_AFTER_DUE_DATE", "La transferencia es posterior al vencimiento.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
