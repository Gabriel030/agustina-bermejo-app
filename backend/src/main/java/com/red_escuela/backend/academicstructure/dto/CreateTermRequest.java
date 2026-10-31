package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateTermRequest(
        @NotNull Integer year, @NotBlank String description, @NotNull TermType type,
        @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
}
