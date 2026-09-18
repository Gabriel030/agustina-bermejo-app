package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;

public record MyExamBoardRegistrationResponse(
        Long id, Long enrollmentId, ExamBoardResponse examBoard, String status, Double score) {

    public static MyExamBoardRegistrationResponse from(ExamBoardRegistration registration, Double score) {
        return new MyExamBoardRegistrationResponse(
                registration.getId(),
                registration.getProgramSubjectSectionEnrollment().getId(),
                ExamBoardResponse.from(registration.getExamBoard()),
                registration.getStatus().name(),
                score);
    }
}
