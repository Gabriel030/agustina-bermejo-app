package com.red_escuela.backend.notification.broadcast.freezer.domain;

public enum BroadcastRecipientStatus {
    EXCLUDED,
    PENDING,
    QUEUED,
    SENT,
    FAILED,
    DONE,
    SKIPPED,
    INVALID_NUMBER
}
