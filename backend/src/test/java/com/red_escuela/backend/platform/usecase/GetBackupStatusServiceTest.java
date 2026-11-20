package com.red_escuela.backend.platform.usecase;

import com.red_escuela.backend.platform.domain.BackupDisk;
import com.red_escuela.backend.platform.domain.BackupState;
import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusSnapshot;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.usecase.backup.GetBackupStatusService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetBackupStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static BackupStatusSnapshot snapshot(Instant generatedAt) {
        return new BackupStatusSnapshot(generatedAt, generatedAt, new BackupDisk(1, 2, "/backups"), List.of(), null, List.of(), 0);
    }

    @Test
    void missingSourceDataIsNotConfiguredWithTheConfiguredTarget() {
        GetBackupStatusService service = new GetBackupStatusService(Optional::empty, CLOCK, 6);

        BackupStatus status = service.read(BackupStatusViewer.DIRECTOR);

        assertThat(status.state()).isEqualTo(BackupState.NOT_CONFIGURED);
        assertThat(status.summary().targetRpoHours()).isEqualTo(6);
    }

    @Test
    void freshSnapshotIsOkAndUsesTheInjectedClock() {
        GetBackupStatusService service = new GetBackupStatusService(() -> Optional.of(snapshot(NOW.minusSeconds(3600))), CLOCK, 4);

        BackupStatus status = service.read(BackupStatusViewer.PLATFORM_ADMIN);

        assertThat(status.state()).isEqualTo(BackupState.OK);
        assertThat(status.summary().hoursSinceLastCompleteBackup()).isEqualTo(1.0);
    }

    @Test
    void oldSnapshotIsStaleAccordingToTheConfiguredFrequency() {
        Instant tenHoursAgo = NOW.minusSeconds(10 * 3600);

        assertThat(new GetBackupStatusService(() -> Optional.of(snapshot(tenHoursAgo)), CLOCK, 4).read(BackupStatusViewer.DIRECTOR).state())
                .isEqualTo(BackupState.STALE);
        assertThat(new GetBackupStatusService(() -> Optional.of(snapshot(tenHoursAgo)), CLOCK, 6).read(BackupStatusViewer.DIRECTOR).state())
                .isEqualTo(BackupState.OK);
    }

    @Test
    void aTargetBelowOneHourIsRejectedAtStartup() {
        assertThatThrownBy(() -> new GetBackupStatusService(Optional::empty, CLOCK, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
