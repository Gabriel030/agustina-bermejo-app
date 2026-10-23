package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ChargebackLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"CHARGEBACK_REFERENCE_INVALID"}, decisions = {"D-206"})
public final class ChargebackReferenceRequiredPolicy implements Policy<PaymentReversalCheck> {

    @Override
    public String ruleName() {
        return "ChargebackReferenceRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentReversalCheck check) {
        boolean invalid = check.reference() == null || check.reference().isBlank() || check.reference().length() > ChargebackLimits.MAX_REFERENCE_LENGTH;
        return invalid
                ? PolicyVerdict.deny(ruleName(), "CHARGEBACK_REFERENCE_INVALID", "La referencia del contracargo es obligatoria y no puede superar los 120 caracteres.")
                : PolicyVerdict.allow(ruleName());
    }
}
