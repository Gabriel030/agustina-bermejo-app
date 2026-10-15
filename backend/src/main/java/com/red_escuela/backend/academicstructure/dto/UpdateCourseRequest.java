package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import jakarta.validation.constraints.Positive;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.NonNegativeMoney;

import java.time.LocalDateTime;

public record UpdateCourseRequest(
        String name, String schedule, @Positive Integer capacity, @NonNegativeMoney Money price,
        @Positive Integer durationMonths, TeachingMode teachingMode,
        LocalDateTime registrationStart, LocalDateTime registrationEnd) {
}
