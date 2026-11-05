package com.red_escuela.backend.billing.discount.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"DISCOUNT_CLOSURE_REASON_REQUIRED"}, decisions = {"D-217", "D-036"})
public final class DiscountClosureReasonRequiredPolicy implements Policy<DiscountClosureCheck> {
    private static final int MAX_REASON_LENGTH = 450;

    @Override
    public String ruleName() {
        return "DiscountClosureReasonRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(DiscountClosureCheck check) {
        boolean missing = check.reason() == null || check.reason().isBlank() || check.reason().strip().length() > MAX_REASON_LENGTH;
        return missing
                ? PolicyVerdict.deny(ruleName(), "DISCOUNT_CLOSURE_REASON_REQUIRED", "Cerrar una promoción necesita un motivo de hasta 450 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
