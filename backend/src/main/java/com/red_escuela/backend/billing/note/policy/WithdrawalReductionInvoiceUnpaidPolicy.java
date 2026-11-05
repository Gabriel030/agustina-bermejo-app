package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE"}, decisions = {"D-204", "D-248", "D-236", "D-253"})
public final class WithdrawalReductionInvoiceUnpaidPolicy implements Policy<WithdrawalLineReductionContext> {

    @Override
    public String ruleName() {
        return "WithdrawalReductionInvoiceUnpaidPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(WithdrawalLineReductionContext context) {
        boolean unpaid = context.invoice().paymentStatus() == InvoicePaymentStatus.PENDING || context.invoice().paymentStatus() == InvoicePaymentStatus.OVERDUE;
        if (context.invoice().documentStatus() == InvoiceDocumentStatus.ISSUED && unpaid && context.invoice().paidAmount().value().isZero()) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE", "Solo se reducen por la baja las facturas emitidas, impagas y sin pagos");
    }
}
