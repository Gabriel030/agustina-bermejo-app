package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import org.springframework.stereotype.Component;

@Component
public class CourseReminderScheduleScopeHandler extends ReminderScheduleScopeHandler {

    public CourseReminderScheduleScopeHandler(ConfigurationPolicyEvaluator policyEvaluator) {
        super(policyEvaluator);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COURSE_REMINDER_SCHEDULE;
    }

    @Override
    ReminderSchedule scheduleOf(ConfigurationValues values) {
        return ((CourseReminderSchedule) values).schedule();
    }
}
