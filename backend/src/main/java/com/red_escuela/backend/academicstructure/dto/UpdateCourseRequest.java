package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCourseRequest(
        String name, String schedule, @Positive Integer capacity, @PositiveOrZero BigDecimal price,
        @Positive Integer durationMonths, TeachingMode teachingMode,
        LocalDateTime registrationStart, LocalDateTime registrationEnd) {
}
