package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateTermRequest(
        @NotNull Integer year, @NotBlank String description, @NotBlank String type,
        @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
}
