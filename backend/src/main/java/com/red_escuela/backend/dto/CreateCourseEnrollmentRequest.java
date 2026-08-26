package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateCourseEnrollmentRequest(
        @NotNull Long courseId,
        // courseSubjectId -> sectionId, solo hace falta cuando un módulo tiene más de una comisión en el Term del curso
        Map<Long, Long> sectionOverrides) {
}
