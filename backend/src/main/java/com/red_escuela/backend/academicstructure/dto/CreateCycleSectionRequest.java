package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateCycleSectionRequest(
        @NotBlank String name,
        Long subjectId,
        Long courseSubjectId,
        @NotNull @Positive Integer capacity,
        List<ScheduleSlotRequest> slots
) {}
