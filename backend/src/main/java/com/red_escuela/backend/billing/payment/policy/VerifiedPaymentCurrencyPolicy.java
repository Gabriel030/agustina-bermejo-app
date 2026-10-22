package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"VERIFIED_PAYMENT_CURRENCY_MISMATCH"}, decisions = {"D-097", "D-098"})
public final class VerifiedPaymentCurrencyPolicy implements Policy<PaymentVerificationCheck> {

    @Override
    public String ruleName() {
        return "VerifiedPaymentCurrencyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentVerificationCheck check) {
        if (check.verifiedAmount() == null || check.verifiedAmount().currency() == check.accountCurrency()) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), "VERIFIED_PAYMENT_CURRENCY_MISMATCH", "La moneda del monto verificado debe ser la de la cuenta del alumno.");
    }
}
