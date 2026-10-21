package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "RECEIPT_UNDER_REVIEW", decisions = {"D-346", "D-186"})
public final class ReceiptUnderReviewSuppressesReminderPolicy implements Policy<ReminderInvoiceFact> {

    @Override
    public String ruleName() {
        return "ReceiptUnderReviewSuppressesReminderPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ReminderInvoiceFact context) {
        boolean underReview = context.receiptUnderReview() || context.paymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW;
        return underReview
                ? PolicyVerdict.deny(ruleName(), "RECEIPT_UNDER_REVIEW", "El comprobante está en revisión: la factura no recibe avisos hasta que se resuelva")
                : PolicyVerdict.allow(ruleName());
    }
}
