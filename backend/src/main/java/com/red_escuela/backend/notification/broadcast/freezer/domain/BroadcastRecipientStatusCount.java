package com.red_escuela.backend.notification.broadcast.freezer.domain;

public record BroadcastRecipientStatusCount(Long broadcastId, BroadcastRecipientStatus status, long total) {
}
