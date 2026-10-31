package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.evaluation.api.GradeItemView;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "GradeItemResponse")
public record StudentGradeItemResponse(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        Grade score, LocalDateTime date, Long examBoardId) {

    public static StudentGradeItemResponse from(GradeItemView view) {
        return new StudentGradeItemResponse(
                view.id(), view.enrollmentId(), view.type(), view.sequenceNumber(),
                view.score(), view.date(), view.examBoardId());
    }
}
