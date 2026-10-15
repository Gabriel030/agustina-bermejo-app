package com.red_escuela.backend.academicstructure.dto;

import java.math.BigDecimal;
import com.red_escuela.backend.academicstructure.entity.RequirementType;
import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
        @NotNull Long prerequisiteProgramSubjectId, @NotNull RequirementType requirementType,
        BigDecimal minGrade, String notes) {
}
