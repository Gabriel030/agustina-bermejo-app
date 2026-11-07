package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_TERMS_OUTDATED"}, decisions = {"D-236"})
public final class AnnualUpfrontTermsCurrentPolicy implements Policy<AnnualUpfrontTermsAcceptance> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontTermsCurrentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontTermsAcceptance acceptance) {
        return acceptance.acceptsTheCurrentVersion()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_TERMS_OUTDATED", "Los términos del pago anual cambiaron: hay que aceptar la versión vigente.");
    }
}
