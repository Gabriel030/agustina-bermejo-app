package com.red_escuela.backend.graduation.procedure.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ProcedureEligibilityResponse(StudentProcedureType type, boolean eligible, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long enrollmentId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String summary, List<ProcedureIneligibilityReason> reasons) {
}
