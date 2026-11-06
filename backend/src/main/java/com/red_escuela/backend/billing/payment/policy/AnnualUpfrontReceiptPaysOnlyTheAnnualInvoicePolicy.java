package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_RECEIPT_MUST_BE_ALONE"}, decisions = {"D-138"})
public final class AnnualUpfrontReceiptPaysOnlyTheAnnualInvoicePolicy implements Policy<AnnualUpfrontReceiptCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontReceiptPaysOnlyTheAnnualInvoicePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontReceiptCheck check) {
        return check.selectedInvoices().size() > 1
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_RECEIPT_MUST_BE_ALONE", "El comprobante del pago anual no puede incluir otras facturas.")
                : PolicyVerdict.allow(ruleName());
    }
}
