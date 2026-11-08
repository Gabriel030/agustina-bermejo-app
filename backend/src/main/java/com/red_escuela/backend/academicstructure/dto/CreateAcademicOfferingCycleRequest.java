package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import jakarta.validation.constraints.NotNull;

public record CreateAcademicOfferingCycleRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long programId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long courseId,
        @NotNull Long termId
) implements SelfValidatingRequest {
    public CreateAcademicOfferingCycleRequest {
        if (programId == null && courseId == null) {
            throw new RequestFieldRequiredException("programId o courseId");
        }
    }
}
