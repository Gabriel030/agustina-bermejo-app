package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_DATE_IN_FUTURE"}, decisions = {"D-169", "D-320"})
public final class VerifiedPaymentDatePolicy implements Policy<PaymentVerificationCheck> {

    @Override
    public String ruleName() {
        return "VerifiedPaymentDatePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentVerificationCheck check) {
        return check.transferDate() != null && check.transferDate().isAfter(check.today())
                ? PolicyVerdict.deny(ruleName(), "PAYMENT_DATE_IN_FUTURE", "La fecha verificada de la transferencia no puede ser futura.")
                : PolicyVerdict.allow(ruleName());
    }
}
