package com.red_escuela.backend.notification.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import java.time.Instant;
import java.util.Map;

public record InvitationWaveResponse(Long id, String name, InvitationWaveStatus status, int batchSize, int intervalMinutes,
                                     int dailyCap, Long importBatchId, Long createdByPersonId, Instant createdAt,
                                     @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant startedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long startedByPersonId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant pausedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long pausedByPersonId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant resumedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long resumedByPersonId,
                                     @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant completedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant cancelledAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long cancelledByPersonId,
                                     long eligibleCount, long queuedTotal, long sentTotal, long failedTotal,
                                     long excludedTotal, long activatedTotal,
                                     @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String importBatchName, long invitableCount,
                                     Map<InvitationExclusionReason, Long> excludedByReason, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant nextRunAt) {
}
