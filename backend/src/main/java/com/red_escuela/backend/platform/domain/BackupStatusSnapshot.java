package com.red_escuela.backend.platform.domain;

import java.time.Instant;
import java.util.List;

public record BackupStatusSnapshot(Instant generatedAt, int targetRpoHours, Instant lastCompleteBackupAt, BackupDisk disk,
                                   List<BackupTierStatus> tiers, RestoreTestStatus lastRestoreTest,
                                   List<BackupAlert> alerts, int consecutiveFailures) {
}
