package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateCourseRequest(
        @NotBlank String name, @NotNull Long termId, String schedule,
        @NotNull @Positive Integer capacity, BigDecimal price, Integer durationMonths,
        LocalDateTime registrationStart, LocalDateTime registrationEnd) {
}
