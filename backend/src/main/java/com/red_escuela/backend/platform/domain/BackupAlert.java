package com.red_escuela.backend.platform.domain;

import java.time.Instant;

public record BackupAlert(Instant at, BackupAlertLevel level, String message) {
}
