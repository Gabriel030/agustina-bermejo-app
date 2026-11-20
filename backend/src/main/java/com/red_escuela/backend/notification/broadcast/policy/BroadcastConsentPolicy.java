package com.red_escuela.backend.notification.broadcast.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OPTED_OUT", "NO_CONSENT"}, decisions = {"E29D-FASE1-CONSENT-POLICY"})
public final class BroadcastConsentPolicy implements Policy<BroadcastDeliveryContext> {

    @Override
    public String ruleName() {
        return "BroadcastConsentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BroadcastDeliveryContext context) {
        if (context.consent().allowed()) {
            return PolicyVerdict.allow(ruleName());
        }
        return PolicyVerdict.deny(ruleName(), context.consent().denialReason().name(),
                "La persona no consintió recibir comunicaciones por este canal");
    }
}
