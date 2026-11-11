package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.domain.ClosureBlockedReason;
import io.swagger.v3.oas.annotations.media.Schema;

public record ClosureSheetAvailabilityResponse(
        boolean canConfirm,
        boolean canApprove,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) ClosureBlockedReason blockedReason) {
}
