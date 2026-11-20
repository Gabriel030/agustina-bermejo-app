package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.domain.BackupTier;
import com.red_escuela.backend.platform.domain.BackupTierStatus;
import com.red_escuela.backend.platform.domain.BackupVerification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record BackupTierResponse(
        BackupTier tier,
        Instant takenAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Versión publicada en PRE_RELEASE; null en el resto de los niveles") String label,
        long dumpSizeBytes,
        long filesSizeBytes,
        BackupVerification verification,
        int kept,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Campo técnico: solo PLATFORM_ADMIN; null para DIRECTOR") List<String> fileNames) {

    public static BackupTierResponse of(BackupTierStatus tier, BackupStatusViewer viewer) {
        return new BackupTierResponse(tier.tier(), tier.takenAt(), tier.label(), tier.dumpSizeBytes(), tier.filesSizeBytes(),
                tier.verification(), tier.kept(), viewer.seesTechnicalFields() ? tier.fileNames() : null);
    }
}
