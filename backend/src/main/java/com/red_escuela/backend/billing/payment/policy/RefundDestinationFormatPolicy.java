package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_DESTINATION_INVALID", "REFUND_DESTINATION_REQUIRED"}, decisions = {"D-267"})
public final class RefundDestinationFormatPolicy implements Policy<RefundRequestCheck> {

    @Override
    public String ruleName() {
        return "RefundDestinationFormatPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundRequestCheck check) {
        RefundDestination destination = check.requestedDestination();
        if (destination == null || !destination.isComplete()) {
            return PolicyVerdict.deny(ruleName(), "REFUND_DESTINATION_REQUIRED", "Indicá el CBU o alias y el titular de la cuenta a la que se devuelve el dinero.");
        }
        return destination.exceedsColumnLimits()
                ? PolicyVerdict.deny(ruleName(), "REFUND_DESTINATION_INVALID",
                "El CBU o alias tiene hasta " + RefundDestination.MAX_ACCOUNT_LENGTH + " caracteres y el titular hasta " + RefundDestination.MAX_HOLDER_LENGTH + ".")
                : PolicyVerdict.allow(ruleName());
    }
}
