package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_MONTHLY_ALREADY_USED"}, decisions = {"D-520", "D-521"})
public final class AnnualUpfrontMonthlyInstallmentAlreadyUsedPolicy implements Policy<AnnualUpfrontRequestCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontMonthlyInstallmentAlreadyUsedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontRequestCheck check) {
        return check.monthlyInstallmentPaidOrStarted()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_MONTHLY_ALREADY_USED", "Ya pagaste o iniciaste el pago de una cuota mensual de este año: la opción de pagar el año completo ya no está disponible.")
                : PolicyVerdict.allow(ruleName());
    }
}
