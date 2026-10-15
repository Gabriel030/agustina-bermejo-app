package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;

public record ReminderScheduleCheck(ReminderSchedule schedule, boolean exceptionTopic) {
}
