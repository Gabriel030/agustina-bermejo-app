package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record OwnWaitlistPositionResponse(WaitlistKind kind, Long queueId, String queueName, Long enrollmentId,
                                          int position, int totalWaiting,
                                          @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate expiresOn) {
}
