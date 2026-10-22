package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"VERIFIED_PAYMENT_AMOUNT_INVALID"}, decisions = {"D-139", "D-208"})
public final class VerifiedPaymentAmountPolicy implements Policy<PaymentVerificationCheck> {

    @Override
    public String ruleName() {
        return "VerifiedPaymentAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentVerificationCheck check) {
        if (check.verifiedAmount() == null) {
            return PolicyVerdict.allow(ruleName());
        }
        boolean invalid = CashPaymentLimits.isNotPositive(check.verifiedAmount()) || CashPaymentLimits.exceedsStorableAmount(check.verifiedAmount());
        return invalid
                ? PolicyVerdict.deny(ruleName(), "VERIFIED_PAYMENT_AMOUNT_INVALID", "El monto verificado debe ser mayor a cero y no superar 9.999.999.999,99.")
                : PolicyVerdict.allow(ruleName());
    }
}
