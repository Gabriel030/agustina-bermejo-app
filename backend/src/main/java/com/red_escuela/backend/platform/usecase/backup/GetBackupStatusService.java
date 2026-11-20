package com.red_escuela.backend.platform.usecase.backup;

import com.red_escuela.backend.platform.domain.BackupState;
import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.domain.BackupSummary;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetBackupStatusService implements GetBackupStatusUseCase {

    private static final int TARGET_RPO_HOURS = 4;

    @Override
    public BackupStatus read(BackupStatusViewer viewer) {
        return new BackupStatus(BackupState.NOT_CONFIGURED, new BackupSummary(null, null, null, TARGET_RPO_HOURS, 0),
                null, List.of(), null, null, List.of());
    }
}
