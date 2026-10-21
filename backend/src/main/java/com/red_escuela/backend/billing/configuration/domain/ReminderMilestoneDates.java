package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record ReminderMilestoneDates(LocalDate issueDate, LocalDate dueDate, LocalDate suspensionDate) {

    public ReminderMilestoneDates {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
    }

    public static ReminderMilestoneDates withoutSuspension(LocalDate issueDate, LocalDate dueDate) {
        return new ReminderMilestoneDates(issueDate, dueDate, null);
    }

    public Optional<LocalDate> dateOf(ReminderMilestone milestone) {
        return switch (milestone) {
            case ISSUANCE -> Optional.of(issueDate);
            case DUE_DATE -> Optional.of(dueDate);
            case SUSPENSION -> Optional.ofNullable(suspensionDate);
        };
    }
}
