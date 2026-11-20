package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record BackupSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant lastCompleteBackupAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double hoursSinceLastCompleteBackup,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Double potentialLossHours,
        int targetRpoHours,
        int consecutiveFailures) {

    public static BackupSummaryResponse of(BackupSummary summary) {
        return new BackupSummaryResponse(summary.lastCompleteBackupAt(), summary.hoursSinceLastCompleteBackup(),
                summary.potentialLossHours(), summary.targetRpoHours(), summary.consecutiveFailures());
    }
}
