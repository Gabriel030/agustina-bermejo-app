package com.red_escuela.backend.notification.invitation.dto;

import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;

import java.time.Instant;

public record InvitationWaveResponse(Long id, String name, InvitationWaveStatus status, int batchSize, int intervalMinutes,
                                     int dailyCap, Long importBatchId, Long createdByPersonId, Instant createdAt,
                                     Instant startedAt, Instant pausedAt, Instant completedAt, Instant cancelledAt,
                                     long eligibleCount, int sentTotal, int failedTotal, int excludedTotal,
                                     int activatedTotal) {
}
