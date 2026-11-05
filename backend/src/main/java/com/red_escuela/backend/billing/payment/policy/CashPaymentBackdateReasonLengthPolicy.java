package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_BACKDATE_REASON_INVALID"}, decisions = {"D-266"})
public final class CashPaymentBackdateReasonLengthPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentBackdateReasonLengthPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        boolean tooLong = check.backdateReason() != null && check.backdateReason().strip().length() > CashPaymentLimits.MAX_BACKDATE_REASON_LENGTH;
        return tooLong
                ? PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_BACKDATE_REASON_INVALID", "El motivo de la fecha retroactiva admite hasta 500 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
