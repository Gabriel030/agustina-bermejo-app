package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.SubjectSectionEnrollment;

import java.time.LocalDateTime;

public record SectionEnrollmentResponse(
        Long id, Long programEnrollmentId, Long sectionId, String subjectName,
        String status, Integer attemptNumber, LocalDateTime enrolledAt, LocalDateTime droppedAt) {

    public static SectionEnrollmentResponse from(SubjectSectionEnrollment enrollment) {
        return new SectionEnrollmentResponse(
                enrollment.getId(),
                enrollment.getProgramEnrollment().getId(),
                enrollment.getSubjectSection().getId(),
                enrollment.getSubjectSection().getSubject().getName(),
                enrollment.getStatus() == null ? null : enrollment.getStatus().name(),
                enrollment.getAttemptNumber(),
                enrollment.getEnrolledAt(),
                enrollment.getDroppedAt());
    }
}
