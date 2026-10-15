package com.red_escuela.backend.billing.configuration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ChangeReminderScheduleRequest(@NotNull @Valid ReminderScheduleDto schedule, @NotNull LocalDate effectiveFrom, @Size(max = 1000) String changeReason) {
}
