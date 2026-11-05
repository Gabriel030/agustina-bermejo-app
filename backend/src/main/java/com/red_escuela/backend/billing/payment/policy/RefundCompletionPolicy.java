package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_COMPLETION_INVALID"}, decisions = {"D-159"})
public final class RefundCompletionPolicy implements Policy<RefundCompletionCheck> {

    @Override
    public String ruleName() {
        return "RefundCompletionPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundCompletionCheck check) {
        boolean invalidDate = check.completedOn() == null || check.completedOn().isAfter(check.today()) || check.completedOn().isBefore(check.requestedOn());
        boolean invalidReference = check.reference() == null || check.reference().isBlank() || check.reference().strip().length() > RefundLimits.MAX_COMPLETION_REFERENCE_LENGTH;
        return invalidDate || invalidReference
                ? PolicyVerdict.deny(ruleName(), "REFUND_COMPLETION_INVALID",
                "La fecha no puede ser futura ni anterior al pedido y la referencia es obligatoria (hasta " + RefundLimits.MAX_COMPLETION_REFERENCE_LENGTH + " caracteres).")
                : PolicyVerdict.allow(ruleName());
    }
}
