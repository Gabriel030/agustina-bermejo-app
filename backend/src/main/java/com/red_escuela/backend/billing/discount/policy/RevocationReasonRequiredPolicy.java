package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REVOCATION_REASON_REQUIRED"}, decisions = {"D-284"})
public final class RevocationReasonRequiredPolicy implements Policy<StudentDiscountRevocationCheck> {
    public static final int MAX_REASON_LENGTH = 400;

    @Override
    public String ruleName() {
        return "RevocationReasonRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentDiscountRevocationCheck check) {
        boolean invalid = check.reason() == null || check.reason().isBlank() || check.reason().strip().length() > MAX_REASON_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "REVOCATION_REASON_REQUIRED", "Revocar un descuento necesita un motivo de hasta " + MAX_REASON_LENGTH + " caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
