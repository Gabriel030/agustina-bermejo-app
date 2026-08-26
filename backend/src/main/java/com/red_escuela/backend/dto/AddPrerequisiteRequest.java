package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.RequirementType;
import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
        @NotNull Long prerequisiteProgramSubjectId, @NotNull RequirementType requirementType,
        Double minGrade, String notes) {
}
