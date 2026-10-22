package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;

import java.util.Objects;

public record ReminderConfigurationVersions(Long scheduleVersionId, Long exceptionVersionId, Long groupVersionId, Long globalSettingsVersionId) {

    public static ReminderConfigurationVersions none() {
        return new ReminderConfigurationVersions(null, null, null, null);
    }

    public static ReminderConfigurationVersions of(ResolvedReminderSchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        return new ReminderConfigurationVersions(schedule.configurationVersionId(), schedule.exceptionVersionId(), schedule.groupVersionId(),
                schedule.globalSettingsVersionId());
    }
}
