package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;

import java.time.LocalDateTime;

public record CourseEnrollmentResponse(
        Long id, Long studentId, Long courseId, String courseName, String status,
        LocalDateTime enrollmentDate, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime completionDate) {

    public static CourseEnrollmentResponse from(CourseEnrollment courseEnrollment) {
        return new CourseEnrollmentResponse(
                courseEnrollment.getId(),
                courseEnrollment.getStudent().getId(),
                courseEnrollment.getCourse().getId(),
                courseEnrollment.getCourse().getName(),
                courseEnrollment.getStatus() == null ? null : courseEnrollment.getStatus().name(),
                courseEnrollment.getEnrollmentDate(),
                courseEnrollment.getCompletionDate());
    }
}
