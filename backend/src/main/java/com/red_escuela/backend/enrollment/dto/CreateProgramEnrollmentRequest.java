package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateProgramEnrollmentRequest(@NotNull Long programId, Long cohortId) {
    public CreateProgramEnrollmentRequest(Long programId) {
        this(programId, null);
    }
}
