package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupAlert;
import com.red_escuela.backend.platform.domain.BackupAlertLevel;

import java.time.Instant;

public record BackupAlertResponse(Instant at, BackupAlertLevel level, String message) {

    public static BackupAlertResponse of(BackupAlert alert) {
        return new BackupAlertResponse(alert.at(), alert.level(), alert.message());
    }
}
