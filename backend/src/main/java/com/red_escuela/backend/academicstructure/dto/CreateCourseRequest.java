package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.NonNegativeMoney;

import java.time.LocalDateTime;

public record CreateCourseRequest(
        @NotBlank String name, @NotNull Long termId, String schedule,
        @NotNull @Positive Integer capacity, @NonNegativeMoney Money price, @NotNull @Positive Integer durationMonths,
        TeachingMode teachingMode,
        LocalDateTime registrationStart, LocalDateTime registrationEnd, Currency currency) {

    public CreateCourseRequest(String name, Long termId, String schedule, Integer capacity, Money price,
                               Integer durationMonths, TeachingMode teachingMode,
                               LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        this(name, termId, schedule, capacity, price, durationMonths, teachingMode, registrationStart, registrationEnd, null);
    }
}
