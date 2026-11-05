package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CASH_PAYMENT_RECEIPT_NUMBER_INVALID"}, decisions = {"D-136"})
public final class CashPaymentReceiptNumberPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "CashPaymentReceiptNumberPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        boolean invalid = check.receiptNumber() != null && check.receiptNumber().strip().length() > CashPaymentLimits.MAX_RECEIPT_NUMBER_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "CASH_PAYMENT_RECEIPT_NUMBER_INVALID", "El número de recibo tiene hasta 100 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
