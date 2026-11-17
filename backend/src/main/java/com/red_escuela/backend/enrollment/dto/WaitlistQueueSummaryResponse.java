package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record WaitlistQueueSummaryResponse(WaitlistKind kind, Long id, String name, String termName,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer capacity,
                                           long occupied,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long free,
                                           long waiting,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate nextExpiry) {
}
