package com.red_escuela.backend.platform.usecase.backup;

import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;

public interface GetBackupStatusUseCase {
    BackupStatus read(BackupStatusViewer viewer);
}
