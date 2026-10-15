package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"IDEMPOTENCY_KEY_INVALID"}, decisions = {"D-136"})
public final class IdempotencyKeyFormatPolicy implements Policy<CashPaymentCheck> {

    @Override
    public String ruleName() {
        return "IdempotencyKeyFormatPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(CashPaymentCheck check) {
        String key = check.idempotencyKey();
        boolean invalid = key == null || key.isBlank() || key.length() > CashPaymentLimits.MAX_IDEMPOTENCY_KEY_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "IDEMPOTENCY_KEY_INVALID", "La clave de idempotencia es obligatoria y tiene hasta 100 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
