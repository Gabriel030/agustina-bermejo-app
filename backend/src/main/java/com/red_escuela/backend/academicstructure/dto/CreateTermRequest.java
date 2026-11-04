package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.academicstructure.domain.TermType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateTermRequest(
        @NotNull Integer year, @NotBlank String description, @NotNull TermType type,
        @NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) implements SelfValidatingRequest {
    public CreateTermRequest {
        RequestLimits.requireMaxLength("description", description, 255);
        RequestLimits.requireNotAfter("startDate", startDate, "endDate", endDate);
        RequestLimits.requireNotAfter("enrollmentStart", enrollmentStart, "enrollmentEnd", enrollmentEnd);
    }
}
