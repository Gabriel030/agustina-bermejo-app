package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.RequirementType;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
        @NotNull Long prerequisiteProgramSubjectId, @NotNull RequirementType requirementType,
        BigDecimal minGrade, String notes) {
}
