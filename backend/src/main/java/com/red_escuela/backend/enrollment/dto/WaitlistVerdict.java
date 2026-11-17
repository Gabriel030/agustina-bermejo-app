package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WaitlistVerdict(boolean eligible,
                              @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) WaitlistVerdictReason reason) {

    public static WaitlistVerdict passing() {
        return new WaitlistVerdict(true, null);
    }

    public static WaitlistVerdict skippedBecause(WaitlistVerdictReason reason) {
        return new WaitlistVerdict(false, reason);
    }
}
