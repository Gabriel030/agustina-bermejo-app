package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.GradeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 1, max = 4) String code,
        String description,
        Integer weeklyHours,
        Integer weeklyHoursLab,
        GradeMode gradeMode,
        Integer maxFinalExamAttempts) {
}
