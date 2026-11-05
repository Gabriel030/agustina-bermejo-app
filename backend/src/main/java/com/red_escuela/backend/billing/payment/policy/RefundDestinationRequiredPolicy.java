package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_DESTINATION_REQUIRED"}, decisions = {"D-267"})
public final class RefundDestinationRequiredPolicy implements Policy<RefundDestinationCheck> {

    @Override
    public String ruleName() {
        return "RefundDestinationRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundDestinationCheck check) {
        boolean someMissing = check.partDestinations().stream().anyMatch(destination -> !destination.isComplete());
        return someMissing
                ? PolicyVerdict.deny(ruleName(), "REFUND_DESTINATION_REQUIRED", "Indicá el CBU o alias y el titular de la cuenta a la que se devuelve el dinero.")
                : PolicyVerdict.allow(ruleName());
    }
}
