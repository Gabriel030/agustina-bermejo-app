package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_RECEIPT_AMOUNT_MUST_BE_TOTAL"}, decisions = {"D-138"})
public final class AnnualUpfrontReceiptPaysExactTotalPolicy implements Policy<AnnualUpfrontReceiptCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontReceiptPaysExactTotalPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontReceiptCheck check) {
        for (ReceiptInvoiceFact annual : check.annualInvoices()) {
            if (annual.currency() != check.declaredAmount().currency() || annual.outstanding().amount().compareTo(check.declaredAmount().amount()) != 0) {
                return PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_RECEIPT_AMOUNT_MUST_BE_TOTAL",
                        "El comprobante del pago anual tiene que ser por el total exacto, sin pagos parciales.");
            }
        }
        return PolicyVerdict.allow(ruleName());
    }
}
