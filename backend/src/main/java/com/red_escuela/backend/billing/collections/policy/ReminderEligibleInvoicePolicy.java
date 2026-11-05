package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "INVOICE_NOT_REMINDABLE", decisions = {"D-354", "D-242", "D-052", "D-149", "D-131"})
public final class ReminderEligibleInvoicePolicy implements Policy<ReminderInvoiceFact> {
    private static final Set<InvoiceType> REMINDABLE_TYPES = Set.of(InvoiceType.MONTHLY_INSTALLMENT, InvoiceType.ENROLLMENT_FEE);
    private static final Set<InvoicePaymentStatus> REMINDABLE_PAYMENT_STATUSES = Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE);

    @Override
    public String ruleName() {
        return "ReminderEligibleInvoicePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReminderInvoiceFact context) {
        if (context.documentType() != InvoiceDocumentType.INVOICE || !REMINDABLE_TYPES.contains(context.invoiceType())) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_NOT_REMINDABLE", "Solo las cuotas mensuales y las matrículas reciben recordatorio; las notas de débito y los cargos sueltos no");
        }
        if (context.documentStatus() != InvoiceDocumentStatus.ISSUED) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_NOT_REMINDABLE", "Un borrador, una factura esperando pago o una descartada no reciben recordatorio");
        }
        if (!REMINDABLE_PAYMENT_STATUSES.contains(context.paymentStatus())) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_NOT_REMINDABLE", "Solo la factura con cobro pendiente o vencido recibe recordatorio");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
