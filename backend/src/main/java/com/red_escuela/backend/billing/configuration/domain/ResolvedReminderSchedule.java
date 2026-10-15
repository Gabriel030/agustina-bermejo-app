package com.red_escuela.backend.billing.configuration.domain;

import java.util.List;

public record ResolvedReminderSchedule(ReminderScheduleSource source, Long configurationVersionId, Long exceptionVersionId, Long groupVersionId,
                                       Long globalSettingsVersionId, List<ReminderNotice> notices, CollectionReminderSettings settings,
                                       List<TrimmedReminder> trimmedNotices) {

    public ResolvedReminderSchedule {
        notices = List.copyOf(notices);
        trimmedNotices = List.copyOf(trimmedNotices);
    }
}
