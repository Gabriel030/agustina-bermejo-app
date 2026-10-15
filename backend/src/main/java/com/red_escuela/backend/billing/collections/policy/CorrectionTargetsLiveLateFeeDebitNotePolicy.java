package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "LATE_FEE_CORRECTION_TARGET_INVALID", decisions = {"D-244", "D-375"})
public final class CorrectionTargetsLiveLateFeeDebitNotePolicy implements Policy<LateFeeCorrectionContext> {

    @Override
    public String ruleName() {
        return "CorrectionTargetsLiveLateFeeDebitNotePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(LateFeeCorrectionContext context) {
        boolean live = context.documentType() == InvoiceDocumentType.DEBIT_NOTE && context.invoiceType() == InvoiceType.LATE_FEE
                && context.documentStatus() == InvoiceDocumentStatus.ISSUED && context.paymentStatus() != InvoicePaymentStatus.CANCELLED;
        return live ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "LATE_FEE_CORRECTION_TARGET_INVALID", "La corrección de recargo solo se aplica a una nota de débito de recargo emitida y no anulada");
    }
}
