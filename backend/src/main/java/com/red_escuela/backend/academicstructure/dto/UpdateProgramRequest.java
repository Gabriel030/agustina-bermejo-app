package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;

import java.math.BigDecimal;

public record UpdateProgramRequest(
        String name, Integer totalYears,
        BigDecimal price, BigDecimal enrollmentFeeAmount,
        Integer totalCreditsRequired, Integer totalHoursRequired,
        TeachingMode teachingMode, ProgramLevel level,
        String graduationProfile, String admissionRequirements) {
}
