package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.enrollment.api.PrerequisiteBatchResult;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.shared.domain.Grade;

import java.util.List;
import java.util.Optional;

public record ClosureStudentAssessment(
        Long enrollmentId, Long studentId, Optional<ClosureFinalCondition> finalCondition, Grade promotionGrade,
        Boolean attendanceOk, Boolean gradeOk, PaymentsStanding payments, boolean documentsComplete,
        Boolean correlativesOk, Optional<PrerequisiteBatchResult> prerequisites, List<ClosureAlert> alerts) {

    public Boolean administrativeOk() {
        return switch (payments) {
            case OK -> Boolean.TRUE;
            case NOT_OK -> Boolean.FALSE;
            case NOT_VERIFIED -> null;
        };
    }
}
