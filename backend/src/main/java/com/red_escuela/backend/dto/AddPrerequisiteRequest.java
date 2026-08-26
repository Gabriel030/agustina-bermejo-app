package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.RequirementType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
        @NotNull Long prerequisiteProgramSubjectId, @NotNull RequirementType requirementType,
        @DecimalMin("0") @DecimalMax("10") Double minGrade, String notes) {
}
