package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION"}, decisions = {"D-127", "D-091"})
public final class CancellationByFullCreditNotePolicy implements Policy<InvoiceCancellationContext> {

    @Override
    public String ruleName() {
        return "CancellationByFullCreditNotePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(InvoiceCancellationContext context) {
        CreditedInvoiceFacts invoice = context.invoice();
        boolean openIssuedInvoice = invoice.documentStatus() == InvoiceDocumentStatus.ISSUED
                && (invoice.paymentStatus() == InvoicePaymentStatus.PENDING || invoice.paymentStatus() == InvoicePaymentStatus.OVERDUE);
        boolean coversWholeOutstanding = !invoice.outstandingAmount().value().isZero() && invoice.paidAmount().value().isZero()
                && invoice.cancellableAmount().equals(invoice.outstandingAmount());
        if (openIssuedInvoice && coversWholeOutstanding) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION",
                "Solo se cancela una factura emitida, impaga y sin pagos, con una nota de crédito por todo lo pendiente");
    }
}
