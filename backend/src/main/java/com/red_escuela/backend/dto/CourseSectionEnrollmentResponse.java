package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CourseSubjectSectionEnrollment;

import java.time.LocalDateTime;

public record CourseSectionEnrollmentResponse(
        Long id, Long courseEnrollmentId, Long sectionId, String courseSubjectName,
        String status, Integer attemptNumber, LocalDateTime enrolledAt, LocalDateTime droppedAt) {

    public static CourseSectionEnrollmentResponse from(CourseSubjectSectionEnrollment enrollment) {
        return new CourseSectionEnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourseEnrollment().getId(),
                enrollment.getCourseSubjectSection().getId(),
                enrollment.getCourseSubjectSection().getCourseSubject().getName(),
                enrollment.getStatus() == null ? null : enrollment.getStatus().name(),
                enrollment.getAttemptNumber(),
                enrollment.getEnrolledAt(),
                enrollment.getDroppedAt());
    }
}
