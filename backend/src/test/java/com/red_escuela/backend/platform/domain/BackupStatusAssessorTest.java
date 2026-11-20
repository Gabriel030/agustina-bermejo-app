package com.red_escuela.backend.platform.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackupStatusAssessorTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final int RPO_HOURS = 4;

    private static BackupTierStatus tier(BackupVerification verification) {
        return new BackupTierStatus(BackupTier.DAILY, NOW.minus(Duration.ofHours(3)), null, 10, 20, verification, 7, List.of("a.dump"));
    }

    private static BackupStatusSnapshot snapshot(Instant generatedAt, BackupVerification verification, RestoreTestStatus restoreTest,
                                                 List<BackupAlert> alerts, int consecutiveFailures) {
        return new BackupStatusSnapshot(generatedAt, NOW.minus(Duration.ofMinutes(90)), new BackupDisk(10, 100, "/backups"),
                List.of(tier(verification)), restoreTest, alerts, consecutiveFailures);
    }

    private static BackupStatusSnapshot healthy() {
        return snapshot(NOW.minus(Duration.ofHours(1)), BackupVerification.VERIFIED,
                new RestoreTestStatus(NOW.minus(Duration.ofDays(2)), RestoreTestResult.PASSED, null), List.of(), 0);
    }

    private static BackupStatus assess(BackupStatusSnapshot snapshot) {
        return BackupStatusAssessor.assess(snapshot, NOW, RPO_HOURS);
    }

    @Test
    void healthySnapshotIsOk() {
        assertThat(assess(healthy()).state()).isEqualTo(BackupState.OK);
    }

    @Test
    void summaryReportsHoursSinceTheLastCompleteBackupAndThePotentialLoss() {
        BackupSummary summary = assess(healthy()).summary();

        assertThat(summary.hoursSinceLastCompleteBackup()).isEqualTo(1.5);
        assertThat(summary.potentialLossHours()).isEqualTo(1.5);
        assertThat(summary.targetRpoHours()).isEqualTo(RPO_HOURS);
        assertThat(summary.lastCompleteBackupAt()).isEqualTo(NOW.minus(Duration.ofMinutes(90)));
    }

    @Test
    void withoutACompleteBackupTheHoursAreNull() {
        BackupStatusSnapshot snapshot = new BackupStatusSnapshot(NOW, null, null, List.of(), null, List.of(), 0);

        BackupSummary summary = assess(snapshot).summary();

        assertThat(summary.hoursSinceLastCompleteBackup()).isNull();
        assertThat(summary.potentialLossHours()).isNull();
    }

    @Test
    void aDamagedCopyNeedsAttention() {
        BackupStatusSnapshot snapshot = snapshot(NOW, BackupVerification.DAMAGED, null, List.of(), 0);

        assertThat(assess(snapshot).state()).isEqualTo(BackupState.ATTENTION);
    }

    @Test
    void aFailedRestoreTestNeedsAttention() {
        BackupStatusSnapshot snapshot = snapshot(NOW, BackupVerification.VERIFIED,
                new RestoreTestStatus(NOW, RestoreTestResult.FAILED, RestoreTestFailureCode.RESTORE_FAILED), List.of(), 0);

        assertThat(assess(snapshot).state()).isEqualTo(BackupState.ATTENTION);
    }

    @Test
    void aCriticalAlertNeedsAttentionButAWarningDoesNot() {
        BackupStatusSnapshot critical = snapshot(NOW, BackupVerification.VERIFIED, null,
                List.of(new BackupAlert(NOW.minus(Duration.ofDays(1)), BackupAlertLevel.CRITICAL, BackupAlertCode.DISK_CRITICAL)), 0);
        BackupStatusSnapshot warning = snapshot(NOW, BackupVerification.VERIFIED, null,
                List.of(new BackupAlert(NOW.minus(Duration.ofDays(1)), BackupAlertLevel.WARNING, BackupAlertCode.DISK_LOW)), 0);

        assertThat(assess(critical).state()).isEqualTo(BackupState.ATTENTION);
        assertThat(assess(warning).state()).isEqualTo(BackupState.OK);
    }

    @Test
    void consecutiveFailuresNeedAttention() {
        BackupStatusSnapshot snapshot = snapshot(NOW, BackupVerification.VERIFIED, null, List.of(), 1);

        assertThat(assess(snapshot).state()).isEqualTo(BackupState.ATTENTION);
    }

    @Test
    void aSnapshotOlderThanTwiceTheRpoIsStaleAndExactlyTwiceIsNot() {
        BackupStatusSnapshot exactlyTwice = snapshot(NOW.minus(Duration.ofHours(2L * RPO_HOURS)), BackupVerification.VERIFIED, null, List.of(), 0);
        BackupStatusSnapshot justOver = snapshot(NOW.minus(Duration.ofHours(2L * RPO_HOURS)).minusSeconds(1), BackupVerification.VERIFIED, null, List.of(), 0);

        assertThat(assess(exactlyTwice).state()).isEqualTo(BackupState.OK);
        assertThat(assess(justOver).state()).isEqualTo(BackupState.STALE);
    }

    @Test
    void staleWinsOverAttention() {
        BackupStatusSnapshot snapshot = snapshot(NOW.minus(Duration.ofHours(20)), BackupVerification.DAMAGED, null, List.of(), 3);

        assertThat(assess(snapshot).state()).isEqualTo(BackupState.STALE);
    }

    @Test
    void alertsOlderThanThirtyDaysAreDroppedAndDoNotRaiseAttention() {
        BackupAlert old = new BackupAlert(NOW.minus(Duration.ofDays(30)).minusSeconds(1), BackupAlertLevel.CRITICAL, BackupAlertCode.BACKUP_FAILED);
        BackupAlert recent = new BackupAlert(NOW.minus(Duration.ofDays(30)), BackupAlertLevel.WARNING, BackupAlertCode.DISK_LOW);
        BackupStatusSnapshot snapshot = snapshot(NOW, BackupVerification.VERIFIED, null, List.of(old, recent), 0);

        BackupStatus status = assess(snapshot);

        assertThat(status.alerts()).containsExactly(recent);
        assertThat(status.state()).isEqualTo(BackupState.OK);
    }

    @Test
    void alertsAreSortedFromTheNewestToTheOldest() {
        BackupAlert older = new BackupAlert(NOW.minus(Duration.ofDays(3)), BackupAlertLevel.WARNING, BackupAlertCode.DISK_LOW);
        BackupAlert newer = new BackupAlert(NOW.minus(Duration.ofDays(1)), BackupAlertLevel.WARNING, BackupAlertCode.ENCRYPTION_DISABLED);

        BackupStatus status = assess(snapshot(NOW, BackupVerification.VERIFIED, null, List.of(older, newer), 0));

        assertThat(status.alerts()).containsExactly(newer, older);
    }

    @Test
    void notConfiguredCarriesTheTargetAndNoData() {
        BackupStatus status = BackupStatusAssessor.notConfigured(RPO_HOURS);

        assertThat(status.state()).isEqualTo(BackupState.NOT_CONFIGURED);
        assertThat(status.summary().targetRpoHours()).isEqualTo(RPO_HOURS);
        assertThat(status.tiers()).isEmpty();
        assertThat(status.alerts()).isEmpty();
        assertThat(status.generatedAt()).isNull();
    }
}
