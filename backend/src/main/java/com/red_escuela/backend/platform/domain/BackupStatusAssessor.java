package com.red_escuela.backend.platform.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class BackupStatusAssessor {

    private static final Duration ALERT_WINDOW = Duration.ofDays(30);
    private static final int STALE_AFTER_RPO_MULTIPLES = 2;

    private BackupStatusAssessor() {
    }

    public static BackupStatus notConfigured(int targetRpoHours) {
        return new BackupStatus(BackupState.NOT_CONFIGURED, new BackupSummary(null, null, null, targetRpoHours, 0),
                null, List.of(), null, null, List.of());
    }

    public static BackupStatus assess(BackupStatusSnapshot snapshot, Instant now, int targetRpoHours) {
        List<BackupAlert> recentAlerts = snapshot.alerts().stream()
                .filter(alert -> !alert.at().isBefore(now.minus(ALERT_WINDOW)))
                .sorted(Comparator.comparing(BackupAlert::at).reversed())
                .toList();
        Double hoursSinceLastCompleteBackup = hoursBetween(snapshot.lastCompleteBackupAt(), now);
        BackupSummary summary = new BackupSummary(snapshot.lastCompleteBackupAt(), hoursSinceLastCompleteBackup,
                hoursSinceLastCompleteBackup, targetRpoHours, snapshot.consecutiveFailures());
        return new BackupStatus(stateOf(snapshot, recentAlerts, now, targetRpoHours), summary, snapshot.generatedAt(),
                snapshot.tiers(), snapshot.lastRestoreTest(), snapshot.disk(), recentAlerts);
    }

    private static BackupState stateOf(BackupStatusSnapshot snapshot, List<BackupAlert> recentAlerts, Instant now,
                                       int targetRpoHours) {
        if (isStale(snapshot.generatedAt(), now, targetRpoHours)) {
            return BackupState.STALE;
        }
        return needsAttention(snapshot, recentAlerts) ? BackupState.ATTENTION : BackupState.OK;
    }

    private static boolean isStale(Instant generatedAt, Instant now, int targetRpoHours) {
        return Duration.between(generatedAt, now).compareTo(Duration.ofHours((long) STALE_AFTER_RPO_MULTIPLES * targetRpoHours)) > 0;
    }

    private static boolean needsAttention(BackupStatusSnapshot snapshot, List<BackupAlert> recentAlerts) {
        return snapshot.tiers().stream().anyMatch(tier -> tier.verification() == BackupVerification.DAMAGED)
                || (snapshot.lastRestoreTest() != null && snapshot.lastRestoreTest().result() == RestoreTestResult.FAILED)
                || recentAlerts.stream().anyMatch(alert -> alert.level() == BackupAlertLevel.CRITICAL)
                || snapshot.consecutiveFailures() > 0;
    }

    private static Double hoursBetween(Instant since, Instant now) {
        if (since == null) {
            return null;
        }
        double hours = Duration.between(since, now).toSeconds() / 3600.0;
        return Math.round(Math.max(hours, 0) * 10) / 10.0;
    }
}
