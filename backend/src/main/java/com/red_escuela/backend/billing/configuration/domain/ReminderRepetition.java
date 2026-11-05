package com.red_escuela.backend.billing.configuration.domain;

public record ReminderRepetition(int everyDays, ReminderMilestone until) {
}
