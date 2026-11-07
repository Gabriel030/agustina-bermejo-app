package com.red_escuela.backend.billing.configuration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import jakarta.validation.constraints.NotNull;

public record ReminderRepetitionDto(@NotNull Integer everyDays, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) ReminderMilestone until) {

    public ReminderRepetition toRepetition() {
        return new ReminderRepetition(everyDays, until);
    }

    public static ReminderRepetitionDto from(ReminderRepetition repetition) {
        return repetition == null ? null : new ReminderRepetitionDto(repetition.everyDays(), repetition.until());
    }
}
