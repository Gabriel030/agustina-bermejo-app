package com.red_escuela.backend.billing.invoice.policy.issuing;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS", decisions = {"D-157"})
public final class ScholarshipDiscountAppliesPolicy implements Policy<ScholarshipDiscountAppliesPolicy.Offer> {

    public record Offer(FeeType feeType, boolean scholarshipCoversInstallments) {
    }

    @Override
    public String ruleName() {
        return "ScholarshipDiscountAppliesPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(Offer offer) {
        boolean applies = offer.feeType() == FeeType.MONTHLY_INSTALLMENT && offer.scholarshipCoversInstallments();
        return applies ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS", "La beca solo se aplica a cuotas mensuales");
    }
}
