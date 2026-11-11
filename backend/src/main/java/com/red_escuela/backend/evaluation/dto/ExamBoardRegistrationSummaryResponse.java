package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.ExamBoardEnrollmentKind;
import com.red_escuela.backend.evaluation.entity.ExamBoardFinalResult;
import com.red_escuela.backend.evaluation.entity.ExamBoardPriorCondition;
import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;

public record ExamBoardRegistrationSummaryResponse(
        Long id,
        String studentName,
        String dni,
        String carrera,
        Integer attemptNumber,
        String status,
        @Schema(nullable = true) BigDecimal finalScore,
        Long enrollmentId,
        ExamBoardEnrollmentKind enrollmentKind,
        ExamBoardPriorCondition priorCondition,
        @Schema(nullable = true) Grade minApprovalGrade,
        @Schema(nullable = true) Long gradeItemId,
        @Schema(nullable = true) ExamBoardFinalResult result) {
}
