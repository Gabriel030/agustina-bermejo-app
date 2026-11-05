package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CHARGEBACK_DATE_INVALID"}, decisions = {"D-206"})
public final class ChargebackDateWithinPaymentLifePolicy implements Policy<PaymentReversalCheck> {

    @Override
    public String ruleName() {
        return "ChargebackDateWithinPaymentLifePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentReversalCheck check) {
        boolean invalid = check.chargedBackOn() == null || check.chargedBackOn().isAfter(check.today())
                || (check.paidOn() != null && check.chargedBackOn().isBefore(check.paidOn()));
        return invalid
                ? PolicyVerdict.deny(ruleName(), "CHARGEBACK_DATE_INVALID", "La fecha del contracargo no puede ser futura ni anterior a la fecha del pago.")
                : PolicyVerdict.allow(ruleName());
    }
}
