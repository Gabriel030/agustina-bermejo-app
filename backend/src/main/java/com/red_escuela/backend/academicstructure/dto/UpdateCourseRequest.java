package com.red_escuela.backend.academicstructure.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCourseRequest(
        String name, String schedule, Integer capacity, BigDecimal price,
        Integer durationMonths, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
}
