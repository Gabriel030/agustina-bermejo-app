package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DISCOUNT_NOT_APPLICABLE_TO_INVOICE_TYPE", decisions = {"D-129"})
public final class DiscountMatchesInvoiceTypePolicy implements Policy<DiscountContest> {

    @Override
    public String ruleName() {
        return "DiscountMatchesInvoiceTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountContest contest) {
        return contest.candidate().applicableInvoiceType() == contest.invoiceType()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "DISCOUNT_NOT_APPLICABLE_TO_INVOICE_TYPE", "El descuento no corresponde a este tipo de factura");
    }
}
