package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateCourseEnrollmentRequest(
        @NotNull Long courseId,
        Map<Long, Long> sectionOverrides) {
}
