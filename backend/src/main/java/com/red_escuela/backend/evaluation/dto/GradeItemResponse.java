package com.red_escuela.backend.evaluation.dto;

import java.math.BigDecimal;
import com.red_escuela.backend.shared.domain.Grade;

import com.red_escuela.backend.evaluation.entity.GradeItem;

import java.time.LocalDateTime;

public record GradeItemResponse(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        BigDecimal score, LocalDateTime date, Long examBoardId) {

    public static GradeItemResponse from(GradeItem gradeItem) {
        Long enrollmentId = gradeItem.getProgramSubjectSectionEnrollment() != null
                ? gradeItem.getProgramSubjectSectionEnrollment().getId()
                : gradeItem.getCourseSubjectSectionEnrollment().getId();
        return new GradeItemResponse(
                gradeItem.getId(),
                enrollmentId,
                gradeItem.getType().name(),
                gradeItem.getSequenceNumber(),
                Grade.valueOrNull(gradeItem.getScore()),
                gradeItem.getDate(),
                gradeItem.getExamBoard() == null ? null : gradeItem.getExamBoard().getId());
    }
}
