package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_DATE_TOO_OLD"}, decisions = {"D-266", "D-270"})
public final class CashPaymentDateWithinAdministrativeWindowPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentDateWithinAdministrativeWindowPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        boolean tooOld = !check.requestedByDirector() && CashPaymentLimits.isBeyondAdministrativeWindow(check.paidOn(), check.today());
        return tooOld
                ? PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_DATE_TOO_OLD", "El administrativo puede cargar cobros de hasta 5 días atrás; más atrás solo el director, con motivo.")
                : PolicyVerdict.allow(ruleName());
    }
}
