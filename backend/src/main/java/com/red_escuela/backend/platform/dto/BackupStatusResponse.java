package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupState;
import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Estado de los backups. Archivo de estado ausente o corrupto: state NOT_CONFIGURED. "
        + "Campos técnicos solo para PLATFORM_ADMIN (null para DIRECTOR): disk.path y tiers[].fileNames, "
        + "que son solo la ruta del disco y los nombres de los archivos de backup, nunca su contenido. "
        + "Sin texto libre de los scripts: alertas y fallas de restauración llegan como códigos cerrados. "
        + "La respuesta no contiene datos de alumnos ni de pagos (D-484)")
public record BackupStatusResponse(
        BackupState state,
        BackupSummaryResponse summary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant generatedAt,
        List<BackupTierResponse> tiers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) RestoreTestResponse lastRestoreTest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BackupDiskResponse disk,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Alertas: el backend filtra a los últimos 30 días; el script puede escribir más")
        List<BackupAlertResponse> alerts) {

    public static BackupStatusResponse of(BackupStatus status, BackupStatusViewer viewer) {
        return new BackupStatusResponse(status.state(), BackupSummaryResponse.of(status.summary()), status.generatedAt(),
                status.tiers().stream().map(tier -> BackupTierResponse.of(tier, viewer)).toList(),
                RestoreTestResponse.of(status.lastRestoreTest()), BackupDiskResponse.of(status.disk(), viewer),
                status.alerts().stream().map(BackupAlertResponse::of).toList());
    }
}
