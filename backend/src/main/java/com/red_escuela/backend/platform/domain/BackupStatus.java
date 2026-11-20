package com.red_escuela.backend.platform.domain;

import java.time.Instant;
import java.util.List;

public record BackupStatus(BackupState state, BackupSummary summary, Instant generatedAt, List<BackupTierStatus> tiers,
                           RestoreTestStatus lastRestoreTest, BackupDisk disk, List<BackupAlert> alerts) {
}
