package com.red_escuela.backend.billing.configuration.domain;

public enum TeachingGroup {
    PROGRAMS(ConfigurationScope.PROGRAM_REMINDER_SCHEDULE),
    MONTHLY_COURSES(ConfigurationScope.COURSE_REMINDER_SCHEDULE);

    private final ConfigurationScope scope;

    TeachingGroup(ConfigurationScope scope) {
        this.scope = scope;
    }

    public ConfigurationScope scope() {
        return scope;
    }
}
