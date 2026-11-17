package com.red_escuela.backend.notification.broadcast.draft.dto;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;

public record BroadcastDraftResponse(Long id, BroadcastStatus status) {
}
