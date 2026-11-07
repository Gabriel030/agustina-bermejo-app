package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;

public record AddPrerequisiteRequest(
        @NotNull Long prerequisiteProgramSubjectId, @NotNull RequirementType requirementType,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) BigDecimal minGrade, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String notes) {
}
