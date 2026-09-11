package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;

import java.time.LocalDateTime;

public record CourseEnrollmentResponse(
        Long id, Long studentId, Long courseId, String status,
        LocalDateTime enrollmentDate, LocalDateTime completionDate) {

    public static CourseEnrollmentResponse from(CourseEnrollment courseEnrollment) {
        return new CourseEnrollmentResponse(
                courseEnrollment.getId(),
                courseEnrollment.getStudent().getId(),
                courseEnrollment.getCourse().getId(),
                courseEnrollment.getStatus() == null ? null : courseEnrollment.getStatus().name(),
                courseEnrollment.getEnrollmentDate(),
                courseEnrollment.getCompletionDate());
    }
}
