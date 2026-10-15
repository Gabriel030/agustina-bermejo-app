package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_DATE_IN_FUTURE"}, decisions = {"D-169", "D-266"})
public final class CashPaymentDateNotInFuturePolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentDateNotInFuturePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        return check.paidOn().isAfter(check.today())
                ? PolicyVerdict.deny(ruleName(), "PAYMENT_DATE_IN_FUTURE", "La fecha del cobro no puede ser futura.")
                : PolicyVerdict.allow(ruleName());
    }
}
