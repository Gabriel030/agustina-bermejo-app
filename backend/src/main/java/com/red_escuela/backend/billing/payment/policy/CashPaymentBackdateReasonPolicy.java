package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_BACKDATE_REASON_REQUIRED"}, decisions = {"D-266", "D-270"})
public final class CashPaymentBackdateReasonPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentBackdateReasonPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        boolean reasonPresent = check.backdateReason() != null && !check.backdateReason().isBlank();
        boolean reasonTooLong = reasonPresent && check.backdateReason().strip().length() > CashPaymentLimits.MAX_BACKDATE_REASON_LENGTH;
        boolean reasonMissing = !reasonPresent && check.requestedByDirector() && CashPaymentLimits.isBeyondAdministrativeWindow(check.paidOn(), check.today());
        return reasonTooLong || reasonMissing
                ? PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_BACKDATE_REASON_REQUIRED", "Un cobro de más de 5 días atrás necesita un motivo de hasta 500 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
