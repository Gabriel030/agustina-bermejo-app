package com.red_escuela.backend.platform.domain;

import java.time.Instant;
import java.util.List;

public record BackupTierStatus(BackupTier tier, Instant takenAt, String label, long dumpSizeBytes, long filesSizeBytes,
                               BackupVerification verification, int kept, List<String> fileNames) {
}
