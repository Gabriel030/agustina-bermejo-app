package com.red_escuela.backend.platform.usecase.backup;

import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusAssessor;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.domain.BackupStatusSource;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;

@UseCase
public class GetBackupStatusService implements GetBackupStatusUseCase {

    private final BackupStatusSource statusSource;
    private final Clock clock;
    private final int targetRpoHours;

    public GetBackupStatusService(BackupStatusSource statusSource, Clock clock,
                                  @Value("${app.backup.target-rpo-hours:4}") int targetRpoHours) {
        if (targetRpoHours < 1) {
            throw new IllegalArgumentException("app.backup.target-rpo-hours debe ser mayor o igual a 1");
        }
        this.statusSource = statusSource;
        this.clock = clock;
        this.targetRpoHours = targetRpoHours;
    }

    @Override
    public BackupStatus read(BackupStatusViewer viewer) {
        return statusSource.read()
                .map(snapshot -> BackupStatusAssessor.assess(snapshot, clock.instant(), targetRpoHours))
                .orElseGet(() -> BackupStatusAssessor.notConfigured(targetRpoHours));
    }
}
