package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;

import java.time.Instant;

public record ContactConsentEntry(ConsentChannel channel, BroadcastCategory category, boolean granted, Instant recordedAt, boolean hasRecord) {
}
