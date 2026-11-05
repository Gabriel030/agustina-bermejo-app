package com.red_escuela.backend.billing.configuration.domain;

import java.util.List;
import java.util.Optional;

public final class ReminderScheduleResolver {

    private ReminderScheduleResolver() {
    }

    public static ResolvedReminderSchedule resolve(Optional<ReminderScheduleVersion> exception, Optional<ReminderScheduleVersion> group,
                                                   CollectionReminderSettings globalSettings, Long globalSettingsVersionId) {
        Optional<ReminderScheduleVersion> activeException = exception.filter(version -> !version.schedule().followsGroupSchedule());
        CollectionReminderSettings groupSettings = group.map(version -> version.schedule().sendingOverrides().overlayOn(globalSettings)).orElse(globalSettings);
        CollectionReminderSettings settings = activeException.map(version -> version.schedule().sendingOverrides().overlayOn(groupSettings)).orElse(groupSettings);
        ReminderScheduleSource source = activeException.isPresent() ? ReminderScheduleSource.EXCEPTION
                : group.isPresent() ? ReminderScheduleSource.GROUP : ReminderScheduleSource.TEMPLATE;
        Optional<ReminderScheduleVersion> chosen = activeException.isPresent() ? activeException : group;
        ReminderSchedule schedule = chosen.map(ReminderScheduleVersion::schedule).orElseGet(ReminderSchedule::initial);
        List<ReminderNotice> notices = schedule.orderedNotices();
        return new ResolvedReminderSchedule(source, chosen.map(ReminderScheduleVersion::versionId).orElse(null),
                activeException.map(ReminderScheduleVersion::versionId).orElse(null), group.map(ReminderScheduleVersion::versionId).orElse(null),
                globalSettingsVersionId, notices, settings);
    }
}
