package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCurriculumPlanRequest(
        @NotBlank String name,
        @NotNull LocalDate validFrom,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer totalYears,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer totalCreditsRequired,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer totalHoursRequired,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long copyFromPlanId) implements SelfValidatingRequest {
    public CreateCurriculumPlanRequest {
        RequestLimits.requireMaxLength("name", name, RequestLimits.SHORT_TEXT);
        RequestLimits.requireAtMost("totalYears", totalYears, 20);
        RequestLimits.requireAtMost("totalCreditsRequired", totalCreditsRequired, 10000);
        RequestLimits.requireAtMost("totalHoursRequired", totalHoursRequired, 100000);
    }
}
