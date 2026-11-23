package com.red_escuela.backend.notification.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record InvitationImportBatchEntry(Long id, String name, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime committedAt, long studentsPendingActivation) {
}
