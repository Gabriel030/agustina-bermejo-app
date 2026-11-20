package com.red_escuela.backend.platform.domain;

import java.time.Instant;

public record BackupSummary(Instant lastCompleteBackupAt, Double hoursSinceLastCompleteBackup, Double potentialLossHours,
                            int targetRpoHours, int consecutiveFailures) {
}
