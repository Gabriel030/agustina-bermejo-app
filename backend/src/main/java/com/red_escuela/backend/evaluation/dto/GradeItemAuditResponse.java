package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record GradeItemAuditResponse(
        Long id,
        Long gradeItemId,
        GradeItemAuditKind kind,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade previousScore,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Grade newScore,
        String reason,
        Long changedById,
        LocalDateTime changedAt) {

    public static GradeItemAuditResponse from(GradeItemAudit audit) {
        return new GradeItemAuditResponse(audit.getId(), audit.getGradeItemId(), audit.getKind(), audit.getPreviousScore(),
                audit.getNewScore(), audit.getReason(), audit.getChangedById(), audit.getChangedAt());
    }
}
