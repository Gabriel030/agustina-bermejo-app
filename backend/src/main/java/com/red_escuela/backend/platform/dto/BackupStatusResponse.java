package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupState;
import com.red_escuela.backend.platform.domain.BackupStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Estado de los backups. Archivo de estado ausente o corrupto: state NOT_CONFIGURED. "
        + "Campos técnicos solo para PLATFORM_ADMIN (null para DIRECTOR): disk.path y tiers[].fileNames, "
        + "que son solo la ruta del disco y los nombres de los archivos de backup, nunca su contenido. "
        + "La respuesta no contiene datos de alumnos ni de pagos (D-484)")
public record BackupStatusResponse(
        BackupState state,
        BackupSummaryResponse summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant generatedAt,
        List<BackupTierResponse> tiers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) RestoreTestResponse lastRestoreTest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BackupDiskResponse disk,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alertas de los últimos 30 días") List<BackupAlertResponse> alerts) {

    public static BackupStatusResponse of(BackupStatus status) {
        return new BackupStatusResponse(status.state(), BackupSummaryResponse.of(status.summary()), status.generatedAt(),
                status.tiers().stream().map(BackupTierResponse::of).toList(), RestoreTestResponse.of(status.lastRestoreTest()),
                BackupDiskResponse.of(status.disk()), status.alerts().stream().map(BackupAlertResponse::of).toList());
    }
}
