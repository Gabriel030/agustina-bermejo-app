package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "INVALID_COLLECTION_REMINDER_SCHEDULE", decisions = {"D-368"})
public final class ReminderScheduleFollowsGroupPolicy implements Policy<ReminderScheduleCheck> {
    private static final String CODE = "INVALID_COLLECTION_REMINDER_SCHEDULE";

    public String ruleName() {
        return "ReminderScheduleFollowsGroupPolicy";
    }

    public PolicyVerdict evaluateRule(ReminderScheduleCheck check) {
        ReminderSchedule schedule = check.schedule();
        if (!schedule.followsGroupSchedule()) {
            return PolicyVerdict.allow(ruleName());
        }
        if (!check.exceptionTopic()) {
            return PolicyVerdict.deny(ruleName(), CODE, "followsGroupSchedule: solo una excepción puede volver a la lista del grupo.");
        }
        if (!schedule.notices().isEmpty() || !schedule.sendingOverrides().equals(ReminderSendingOverrides.none())) {
            return PolicyVerdict.deny(ruleName(), CODE, "followsGroupSchedule: una excepción que vuelve al grupo no lleva avisos ni ajustes propios.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
