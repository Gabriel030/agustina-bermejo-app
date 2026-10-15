package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import org.springframework.stereotype.Component;

@Component
public class ProgramReminderScheduleScopeHandler extends ReminderScheduleScopeHandler {

    public ProgramReminderScheduleScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        super(policyEvaluator);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.PROGRAM_REMINDER_SCHEDULE;
    }

    @Override
    ReminderSchedule scheduleOf(ConfigurationValues values) {
        return ((ProgramReminderSchedule) values).schedule();
    }
}
