package com.red_escuela.backend.billing.annualupfront.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"IDEMPOTENCY_KEY_INVALID"}, decisions = {"D-136"})
public final class AnnualUpfrontIdempotencyKeyPolicy implements Policy<AnnualUpfrontIdempotencyKey> {
    public static final int MAX_LENGTH = 100;

    @Override
    public String ruleName() {
        return "AnnualUpfrontIdempotencyKeyPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontIdempotencyKey idempotencyKey) {
        String key = idempotencyKey.value();
        boolean invalid = key == null || key.isBlank() || key.length() > MAX_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "IDEMPOTENCY_KEY_INVALID", "La clave de idempotencia es obligatoria y tiene hasta 100 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
