package com.red_escuela.backend.enrollment.api;

import java.time.LocalDateTime;

public record CourseEnrollmentDetailView(
        Long id, Long courseId, String courseName, String status, LocalDateTime enrollmentDate, LocalDateTime completionDate) {
}
