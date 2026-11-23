package com.red_escuela.backend.notification.invitation.dto;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;

import java.time.Instant;

public record InvitationWaveResponse(Long id, String name, InvitationWaveStatus status, int batchSize, int intervalMinutes,
                                     int dailyCap, Long importBatchId, Long createdByPersonId, Instant createdAt,
                                     Instant startedAt, Long startedByPersonId, Instant pausedAt, Long pausedByPersonId,
                                     Instant completedAt, Instant cancelledAt, Long cancelledByPersonId,
                                     long eligibleCount, long queuedTotal, long sentTotal, long failedTotal,
                                     long excludedTotal, long activatedTotal) {
}
