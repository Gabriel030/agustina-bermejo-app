package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.ProgramLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.NonNegativeMoney;


public record CreateProgramRequest(
        @NotBlank String name,
        @NotNull Integer totalYears,
        @NonNegativeMoney Money price,
        @NonNegativeMoney Money enrollmentFeeAmount,
        @NotNull @Min(0) Integer totalCreditsRequired,
        Integer totalHoursRequired,
        TeachingMode teachingMode,
        ProgramLevel level,
        String graduationProfile,
        String admissionRequirements,
        Currency currency) {

    public CreateProgramRequest(String name, Integer totalYears, Money price, Money enrollmentFeeAmount,
                                Integer totalCreditsRequired, Integer totalHoursRequired, TeachingMode teachingMode,
                                ProgramLevel level, String graduationProfile, String admissionRequirements) {
        this(name, totalYears, price, enrollmentFeeAmount, totalCreditsRequired, totalHoursRequired, teachingMode,
                level, graduationProfile, admissionRequirements, null);
    }
}
