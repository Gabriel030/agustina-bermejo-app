package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP"}, decisions = {"D-130", "D-302", "D-388"})
public final class ScholarshipHolderCannotChooseAnnualUpfrontPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "ScholarshipHolderCannotChooseAnnualUpfrontPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.scholarshipCoversYear()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_NOT_AVAILABLE_WITH_SCHOLARSHIP", "Con una beca activa no se puede pagar el año completo.")
                : PolicyVerdict.allow(ruleName());
    }
}
