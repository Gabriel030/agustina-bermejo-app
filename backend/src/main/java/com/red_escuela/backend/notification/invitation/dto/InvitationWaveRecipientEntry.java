package com.red_escuela.backend.notification.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.notification.invitation.domain.InvitationErrorCode;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;

import java.time.Instant;

public record InvitationWaveRecipientEntry(Long personId, InvitationRecipientStatus status,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) InvitationExclusionReason exclusionReason, int attempts, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) InvitationErrorCode lastErrorCode,
                                           @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant sentAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant lastAttemptAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String displayName, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String email) {
}
