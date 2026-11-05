package com.red_escuela.backend.billing.configuration.domain;

public record ProgramReminderSchedule(ReminderSchedule schedule) implements ConfigurationValues {

    public ProgramReminderSchedule {
        if (schedule == null) {
            throw new InvalidConfigurationValuesException("La lista de avisos es obligatoria");
        }
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.PROGRAM_REMINDER_SCHEDULE;
    }

    @Override
    public ConfigurationValues normalized() {
        return new ProgramReminderSchedule(schedule.sortedByTimeline());
    }
}
