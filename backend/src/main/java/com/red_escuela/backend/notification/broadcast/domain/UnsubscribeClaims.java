package com.red_escuela.backend.notification.broadcast.domain;

import java.time.Instant;

public record UnsubscribeClaims(Long personId, ConsentChannel channel, BroadcastCategory category, Instant issuedAt) {
}
