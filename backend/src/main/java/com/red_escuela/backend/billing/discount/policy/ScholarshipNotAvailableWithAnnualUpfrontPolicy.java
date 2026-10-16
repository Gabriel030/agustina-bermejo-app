package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT"}, decisions = {"D-158", "D-388"})
public final class ScholarshipNotAvailableWithAnnualUpfrontPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "ScholarshipNotAvailableWithAnnualUpfrontPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        return check.discountType() == DiscountType.SCHOLARSHIP && check.annualUpfrontCoversRange()
                ? PolicyVerdict.deny(ruleName(), "SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT", "Con el pago anual no se otorga una beca en ese año lectivo.")
                : PolicyVerdict.allow(ruleName());
    }
}
