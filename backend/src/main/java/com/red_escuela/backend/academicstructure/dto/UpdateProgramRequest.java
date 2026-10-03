package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProgramRequest {
    private String name;
    @Min(1)
    private Integer totalYears;
    @PositiveOrZero
    private BigDecimal price;
    @PositiveOrZero
    private BigDecimal enrollmentFeeAmount;
    @Min(0)
    private Integer totalCreditsRequired;
    private Optional<Integer> totalHoursRequired;
    private Optional<TeachingMode> teachingMode;
    private Optional<ProgramLevel> level;
    private Optional<String> graduationProfile;
    private Optional<String> admissionRequirements;
}
