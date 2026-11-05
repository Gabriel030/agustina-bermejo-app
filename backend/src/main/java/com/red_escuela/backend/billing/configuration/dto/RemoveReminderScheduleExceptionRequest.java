package com.red_escuela.backend.billing.configuration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RemoveReminderScheduleExceptionRequest(@NotNull LocalDate effectiveFrom, @Size(max = 1000) String changeReason) {
}
