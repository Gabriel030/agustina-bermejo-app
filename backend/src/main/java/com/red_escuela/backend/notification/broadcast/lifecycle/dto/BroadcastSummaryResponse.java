package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;

import java.time.Instant;

public record BroadcastSummaryResponse(Long id, String name, BroadcastCategory category, ChannelType channel,
                                       BroadcastStatus status, Instant createdAt, Instant confirmedAt) {

    public static BroadcastSummaryResponse from(Broadcast broadcast) {
        return new BroadcastSummaryResponse(broadcast.getId(), broadcast.getName(), broadcast.getCategory(), broadcast.getChannel(),
                broadcast.getStatus(), broadcast.getCreatedAt(), broadcast.getConfirmedAt());
    }
}
