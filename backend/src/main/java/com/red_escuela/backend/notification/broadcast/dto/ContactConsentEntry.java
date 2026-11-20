package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record ContactConsentEntry(ConsentChannel channel, BroadcastCategory category, boolean granted, @Schema(nullable = true) Instant recordedAt, boolean hasRecord) {
}
