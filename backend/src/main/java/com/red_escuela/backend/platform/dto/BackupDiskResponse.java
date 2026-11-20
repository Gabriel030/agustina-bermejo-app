package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupDisk;
import io.swagger.v3.oas.annotations.media.Schema;

public record BackupDiskResponse(
        long freeBytes,
        long totalBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Campo técnico: solo PLATFORM_ADMIN; null para DIRECTOR") String path) {

    public static BackupDiskResponse of(BackupDisk disk) {
        return disk == null ? null : new BackupDiskResponse(disk.freeBytes(), disk.totalBytes(), disk.path());
    }
}
