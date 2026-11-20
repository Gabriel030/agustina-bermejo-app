package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupDisk;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import io.swagger.v3.oas.annotations.media.Schema;

public record BackupDiskResponse(
        long freeBytes,
        long totalBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 256,
                description = "Campo técnico: solo PLATFORM_ADMIN; null para DIRECTOR") String path) {

    public static BackupDiskResponse of(BackupDisk disk, BackupStatusViewer viewer) {
        if (disk == null) {
            return null;
        }
        return new BackupDiskResponse(disk.freeBytes(), disk.totalBytes(), viewer.seesTechnicalFields() ? disk.path() : null);
    }
}
