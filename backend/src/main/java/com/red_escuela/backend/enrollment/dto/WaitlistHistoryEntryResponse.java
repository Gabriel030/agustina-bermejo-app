package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record WaitlistHistoryEntryResponse(LocalDateTime at, String action,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String actorName,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String studentName,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String reason) {
}
