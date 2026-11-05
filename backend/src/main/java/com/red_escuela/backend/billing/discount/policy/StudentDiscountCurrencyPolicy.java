package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CURRENCY_MISMATCH"}, decisions = {"D-097"})
public final class StudentDiscountCurrencyPolicy implements Policy<StudentDiscountGrantCheck> {

    @Override
    public String ruleName() {
        return "StudentDiscountCurrencyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountGrantCheck check) {
        boolean fixedAmountMismatch = check.calculation() instanceof MonetaryAdjustment.Nominal nominal && nominal.amount().currency() != check.enrollmentCurrency();
        boolean capMismatch = check.capPerInvoice() != null && check.capPerInvoice().value().currency() != check.enrollmentCurrency();
        return fixedAmountMismatch || capMismatch
                ? PolicyVerdict.deny(ruleName(), "CURRENCY_MISMATCH", "El monto fijo y el tope deben estar en la moneda de la inscripción.")
                : PolicyVerdict.allow(ruleName());
    }
}
