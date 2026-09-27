package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotNull;

public record CreateAcademicOfferingCycleRequest(
        Long programId,
        Long courseId,
        @NotNull Long termId
) {}
