package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountContest;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "DISCOUNT_OUTBID_BY_A_BETTER_ONE", decisions = {"D-073", "D-074", "D-129"})
public final class BestDiscountPerInvoiceTypePolicy implements Policy<DiscountContest> {

    @Override
    public String ruleName() {
        return "BestDiscountPerInvoiceTypePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountContest contest) {
        boolean outbid = contest.rivals().stream().anyMatch(rival -> rival.offersMoreThan(contest.candidate()));
        return outbid
                ? PolicyVerdict.deny(ruleName(), "DISCOUNT_OUTBID_BY_A_BETTER_ONE", "Otro descuento ofrece un monto mayor para esta factura")
                : PolicyVerdict.allow(ruleName());
    }
}
