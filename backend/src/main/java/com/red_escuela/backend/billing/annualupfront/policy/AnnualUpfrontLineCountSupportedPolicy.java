package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED"}, decisions = {"D-131"})
public final class AnnualUpfrontLineCountSupportedPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontLineCountSupportedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.lineCount() < 1 || check.lineCount() > Invoice.MAX_ANNUAL_UPFRONT_LINES
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_LINE_COUNT_NOT_SUPPORTED", "El año lectivo no tiene entre 1 y 10 cuotas para pagar por adelantado.")
                : PolicyVerdict.allow(ruleName());
    }
}
