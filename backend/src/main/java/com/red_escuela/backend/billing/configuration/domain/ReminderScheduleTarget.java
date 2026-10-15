package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;

public record ReminderScheduleTarget(TeachingGroup group, Long targetId) {

    public ReminderScheduleTarget {
        Objects.requireNonNull(group, "group");
    }
}
