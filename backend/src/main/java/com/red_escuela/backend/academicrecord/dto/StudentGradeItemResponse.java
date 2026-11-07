package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.evaluation.api.GradeItemCorrectionView;
import com.red_escuela.backend.evaluation.api.GradeItemView;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "GradeItemResponse")
public record StudentGradeItemResponse(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        Grade score, LocalDateTime date, Long examBoardId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Correction correction) {

    public static StudentGradeItemResponse from(GradeItemView view) {
        return new StudentGradeItemResponse(
                view.id(), view.enrollmentId(), view.type(), view.sequenceNumber(),
                view.score(), view.date(), view.examBoardId(), Correction.from(view.correction()));
    }

    @Schema(name = "GradeItemCorrectionResponse")
    public record Correction(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade previousScore,
            String reason, LocalDateTime correctedAt) {

        static Correction from(GradeItemCorrectionView view) {
            return view == null ? null : new Correction(view.previousScore(), view.reason(), view.correctedAt());
        }
    }
}
