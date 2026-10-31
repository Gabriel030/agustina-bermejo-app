package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDateTime;

public record EnrollmentGradeItemView(
        Long id, Long enrollmentId, String type, Integer sequenceNumber,
        Grade score, LocalDateTime date, Long examBoardId) {

    static EnrollmentGradeItemView from(GradeItem gradeItem) {
        Long enrollmentId = gradeItem.getProgramSubjectSectionEnrollment() != null
                ? gradeItem.getProgramSubjectSectionEnrollment().getId()
                : gradeItem.getCourseSubjectSectionEnrollment().getId();
        return new EnrollmentGradeItemView(
                gradeItem.getId(),
                enrollmentId,
                gradeItem.getType().name(),
                gradeItem.getSequenceNumber(),
                gradeItem.getScore(),
                gradeItem.getDate(),
                gradeItem.getExamBoard() == null ? null : gradeItem.getExamBoard().getId());
    }
}
