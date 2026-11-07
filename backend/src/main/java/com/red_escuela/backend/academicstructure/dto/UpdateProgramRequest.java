package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import jakarta.validation.constraints.Min;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.NonNegativeMoney;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgramRequest {
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private String name;
    @Min(1)
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Integer totalYears;
    @NonNegativeMoney
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Money price;
    @NonNegativeMoney
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Money enrollmentFeeAmount;
    @Min(0)
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Integer totalCreditsRequired;
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Optional<Integer> totalHoursRequired;
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Optional<TeachingMode> teachingMode;
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Optional<ProgramLevel> level;
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Optional<String> graduationProfile;
    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) private Optional<String> admissionRequirements;
}
