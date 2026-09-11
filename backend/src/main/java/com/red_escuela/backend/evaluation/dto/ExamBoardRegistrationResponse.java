package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;

public record ExamBoardRegistrationResponse(Long id, Long examBoardId, Long enrollmentId, String status) {

    public static ExamBoardRegistrationResponse from(ExamBoardRegistration registration) {
        Long enrollmentId = registration.getProgramSubjectSectionEnrollment() != null
                ? registration.getProgramSubjectSectionEnrollment().getId()
                : registration.getCourseSubjectSectionEnrollment().getId();
        return new ExamBoardRegistrationResponse(
                registration.getId(),
                registration.getExamBoard().getId(),
                enrollmentId,
                registration.getStatus().name());
    }
}
