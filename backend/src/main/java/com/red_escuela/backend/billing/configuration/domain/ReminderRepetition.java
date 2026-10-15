package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;

public record ReminderRepetition(int everyDays, ReminderMilestone until) {

    public ReminderRepetition {
        Objects.requireNonNull(until, "until");
    }
}
