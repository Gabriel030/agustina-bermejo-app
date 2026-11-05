package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_AMOUNT_INVALID"}, decisions = {"D-136", "D-097", "D-098"})
public final class CashPaymentAmountPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        boolean invalid = CashPaymentLimits.isNotPositive(check.amount()) || CashPaymentLimits.exceedsStorableAmount(check.amount());
        return invalid
                ? PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_AMOUNT_INVALID", "El monto del cobro debe ser mayor a cero y no superar 9.999.999.999,99.")
                : PolicyVerdict.allow(ruleName());
    }
}
