package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank String name,
        @NotBlank @Size(min = 1, max = 4) String code,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String description,
        @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer weeklyHours,
        @PositiveOrZero @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer weeklyHoursLab,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) GradeMode gradeMode,
        @Positive @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer maxFinalExamAttempts) {
}
