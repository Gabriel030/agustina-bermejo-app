package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.evaluation.api.EnrollmentGradeItemView;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "GradeItemResponse")
public record StudentGradeItemResponse(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        BigDecimal score, LocalDateTime date, Long examBoardId) {

    public static StudentGradeItemResponse from(EnrollmentGradeItemView view) {
        return new StudentGradeItemResponse(
                view.id(), view.enrollmentId(), view.type(), view.sequenceNumber(),
                Grade.valueOrNull(view.score()), view.date(), view.examBoardId());
    }
}
