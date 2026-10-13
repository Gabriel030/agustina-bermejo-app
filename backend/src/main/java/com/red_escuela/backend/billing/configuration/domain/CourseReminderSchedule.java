package com.red_escuela.backend.billing.configuration.domain;

public record CourseReminderSchedule(ReminderSchedule schedule) implements ConfigurationValues {

    public CourseReminderSchedule {
        if (schedule == null) {
            throw new InvalidConfigurationValuesException("La lista de avisos es obligatoria");
        }
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COURSE_REMINDER_SCHEDULE;
    }
}
