package com.red_escuela.backend.enrollment.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record FinalExamEligibilityResponse(boolean eligible, @Schema(nullable = true) FinalExamIneligibilityReason reason) {

    public static FinalExamEligibilityResponse allowed() {
        return new FinalExamEligibilityResponse(true, null);
    }

    public static FinalExamEligibilityResponse blockedBy(FinalExamIneligibilityReason reason) {
        return new FinalExamEligibilityResponse(false, reason);
    }
}
