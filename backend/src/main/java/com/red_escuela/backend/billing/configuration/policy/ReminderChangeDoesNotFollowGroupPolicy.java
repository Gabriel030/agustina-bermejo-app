package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SCHEDULE", decisions = {"D-368"})
public final class ReminderChangeDoesNotFollowGroupPolicy implements Policy<ReminderScheduleChangeCheck> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SCHEDULE";

    public String ruleName() {
        return "ReminderChangeDoesNotFollowGroupPolicy";
    }

    public PolicyVerdict evaluateRule(ReminderScheduleChangeCheck check) {
        if (check.schedule().followsGroupSchedule()) {
            return PolicyVerdict.deny(ruleName(), CODE, "followsGroupSchedule: usá /removal para volver a la política del grupo.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
