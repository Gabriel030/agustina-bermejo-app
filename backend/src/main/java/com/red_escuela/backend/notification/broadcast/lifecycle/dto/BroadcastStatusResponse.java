package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;

public record BroadcastStatusResponse(Long id, BroadcastStatus status) {
}
