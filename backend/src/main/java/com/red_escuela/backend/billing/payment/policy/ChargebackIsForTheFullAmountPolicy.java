package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT"}, decisions = {"D-262"})
public final class ChargebackIsForTheFullAmountPolicy implements Policy<PaymentReversalCheck> {

    @Override
    public String ruleName() {
        return "ChargebackIsForTheFullAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentReversalCheck check) {
        return check.creditedAmount().equals(check.requestedAmount())
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT", "El contracargo es siempre por el total del pago.");
    }
}
