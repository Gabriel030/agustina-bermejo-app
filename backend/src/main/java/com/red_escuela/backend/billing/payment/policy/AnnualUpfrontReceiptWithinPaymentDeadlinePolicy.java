package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_RECEIPT_LATE"}, decisions = {"D-155", "D-154"})
public final class AnnualUpfrontReceiptWithinPaymentDeadlinePolicy implements Policy<AnnualUpfrontReceiptCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontReceiptWithinPaymentDeadlinePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontReceiptCheck check) {
        for (ReceiptInvoiceFact annual : check.annualInvoices()) {
            if (annual.paymentDeadline() == null) {
                continue;
            }
            if (check.startedAt().isAfter(annual.paymentDeadline())) {
                return PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_RECEIPT_LATE", check.cashPayment()
                        ? "El cobro en efectivo se registró después del plazo de pago del pedido anual."
                        : "El comprobante se subió después del plazo de pago del pedido anual.");
            }
            if (check.transferDate().isAfter(annual.paymentDeadline().toLocalDate())) {
                return PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_RECEIPT_LATE", "La transferencia es posterior al plazo de pago del pedido anual.");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
