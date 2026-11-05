package com.red_escuela.backend.notification.delivery.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OPTED_OUT"}, decisions = {"D-340", "D-372"})
public final class PreDueEmailOptOutPolicy implements Policy<EmailDeliveryContext> {

    @Override
    public String ruleName() {
        return "PreDueEmailOptOutPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(EmailDeliveryContext context) {
        if (context.precedesDueDate() && !context.preDueEmailEnabled()) {
            return PolicyVerdict.deny(ruleName(), "OPTED_OUT", "El alumno apagó los mails previos al vencimiento");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
