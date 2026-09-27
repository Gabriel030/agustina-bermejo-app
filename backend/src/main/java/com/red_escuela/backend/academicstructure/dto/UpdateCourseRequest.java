package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCourseRequest(
        String name, String schedule, Integer capacity, BigDecimal price,
        Integer durationMonths, TeachingMode teachingMode,
        LocalDateTime registrationStart, LocalDateTime registrationEnd) {
}
