package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.RestoreTestFailureCode;
import com.red_escuela.backend.platform.domain.RestoreTestResult;
import com.red_escuela.backend.platform.domain.RestoreTestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record RestoreTestResponse(
        Instant ranAt,
        RestoreTestResult result,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Motivo de la falla; null si result es PASSED") RestoreTestFailureCode failureCode) {

    public static RestoreTestResponse of(RestoreTestStatus test) {
        return test == null ? null : new RestoreTestResponse(test.ranAt(), test.result(), test.failureCode());
    }
}
