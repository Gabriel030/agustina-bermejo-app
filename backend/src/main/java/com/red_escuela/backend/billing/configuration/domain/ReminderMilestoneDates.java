package com.red_escuela.backend.billing.configuration.domain;

import java.time.LocalDate;
import java.util.Objects;

public record ReminderMilestoneDates(LocalDate issueDate, LocalDate dueDate, LocalDate suspensionDate) {

    public ReminderMilestoneDates {
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(suspensionDate, "suspensionDate");
    }

    public LocalDate dateOf(ReminderMilestone milestone) {
        return switch (milestone) {
            case ISSUANCE -> issueDate;
            case DUE_DATE -> dueDate;
            case SUSPENSION -> suspensionDate;
        };
    }
}
