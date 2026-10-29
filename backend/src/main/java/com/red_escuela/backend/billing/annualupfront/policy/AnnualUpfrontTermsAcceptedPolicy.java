package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED"}, decisions = {"D-236"})
public final class AnnualUpfrontTermsAcceptedPolicy implements Policy<AnnualUpfrontTermsAcceptance> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontTermsAcceptedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontTermsAcceptance acceptance) {
        return acceptance.acceptsTheCurrentVersion()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED", "Para pedir el pago anual hay que aceptar la condición vigente.");
    }
}
