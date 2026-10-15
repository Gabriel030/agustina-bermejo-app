package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SETTINGS", decisions = {"D-351", "D-373"})
public final class CollectionReminderSettingsCoherencePolicy implements Policy<CollectionReminderSettings> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SETTINGS";

    public String ruleName() {
        return "CollectionReminderSettingsCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(CollectionReminderSettings settings) {
        String violation = ReminderSendingRules.firstViolationOf(settings.asOverrides());
        return violation == null ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), CODE, violation);
    }
}
