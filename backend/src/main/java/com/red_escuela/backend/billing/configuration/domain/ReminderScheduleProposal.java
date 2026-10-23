package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;
import java.util.Optional;

public record ReminderScheduleProposal(ReminderScheduleTarget target, ReminderSchedule schedule, CollectionReminderSettings globalSettings) {

    public ReminderScheduleProposal {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(schedule, "schedule");
    }

    public Optional<CollectionReminderSettings> proposedGlobalSettings() {
        return Optional.ofNullable(globalSettings);
    }
}
