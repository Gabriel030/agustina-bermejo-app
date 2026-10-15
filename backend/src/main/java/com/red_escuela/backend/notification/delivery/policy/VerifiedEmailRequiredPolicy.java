package com.red_escuela.backend.notification.delivery.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NO_VERIFIED_EMAIL"}, decisions = {"D-354"})
public final class VerifiedEmailRequiredPolicy implements Policy<EmailDeliveryContext> {

    @Override
    public String ruleName() {
        return "VerifiedEmailRequiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(EmailDeliveryContext context) {
        if (!context.emailVerified()) {
            return PolicyVerdict.deny(ruleName(), "NO_VERIFIED_EMAIL", "El destinatario no tiene un mail verificado");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
