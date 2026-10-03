package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.shared.domain.Grade;
import java.math.BigDecimal;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;

public record MyExamBoardRegistrationResponse(
        Long id, Long enrollmentId, ExamBoardResponse examBoard, String status, BigDecimal score) {

    public static MyExamBoardRegistrationResponse from(ExamBoardRegistration registration, Grade score) {
        return new MyExamBoardRegistrationResponse(
                registration.getId(),
                registration.getProgramSubjectSectionEnrollment().getId(),
                ExamBoardResponse.from(registration.getExamBoard()),
                registration.getStatus().name(),
                Grade.valueOrNull(score));
    }
}
