package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SCHEDULE", decisions = {"D-368"})
public final class ReminderScheduleSizePolicy implements Policy<ReminderScheduleCheck> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SCHEDULE";

    public String ruleName() {
        return "ReminderScheduleSizePolicy";
    }

    public PolicyVerdict evaluateRule(ReminderScheduleCheck check) {
        if (check.schedule().followsGroupSchedule()) {
            return PolicyVerdict.allow(ruleName());
        }
        String violation = ReminderNoticeRules.sizeViolation(check.schedule().notices());
        return violation == null ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), CODE, violation);
    }
}
