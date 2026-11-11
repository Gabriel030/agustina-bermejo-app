package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureStatusFact;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record CommissionClosureListItemResponse(
        ClosureSectionKind sectionKind,
        Long sectionId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Long closureId,
        String subjectName,
        String sectionName,
        Long termId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String termLabel,
        List<String> instructorNames,
        long studentCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Long promoteCount,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Long toReviewCount,
        ClosureStatus status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) LocalDateTime statusDate,
        ClosureStatusFact statusFact,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String reportNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Integer reportVersion,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String book,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String folio) {
}
