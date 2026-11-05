package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SCHEDULE", decisions = {"D-369", "D-373"})
public final class ReminderSendingOverridesCoherencePolicy implements Policy<ReminderScheduleCheck> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SCHEDULE";

    public String ruleName() {
        return "ReminderSendingOverridesCoherencePolicy";
    }

    public PolicyVerdict evaluateRule(ReminderScheduleCheck check) {
        String violation = ReminderSendingRules.firstViolationOf(check.schedule().sendingOverrides());
        return violation == null ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), CODE, "sendingOverrides: " + violation);
    }
}
