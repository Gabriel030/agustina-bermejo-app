package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

public record BroadcastSummaryResponse(Long id, String name, BroadcastCategory category, ChannelType channel,
                                       BroadcastStatus status, Instant createdAt, Instant confirmedAt,
                                       @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String createdByName,
                                       @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String confirmedByName,
                                       Map<BroadcastRecipientStatus, Long> recipientCounts) {

    public static BroadcastSummaryResponse from(Broadcast broadcast, Map<Long, String> names,
                                                Map<BroadcastRecipientStatus, Long> recipientCounts) {
        return new BroadcastSummaryResponse(broadcast.getId(), broadcast.getName(), broadcast.getCategory(), broadcast.getChannel(),
                broadcast.getStatus(), broadcast.getCreatedAt(), broadcast.getConfirmedAt(), names.get(broadcast.getCreatedByPersonId()),
                names.get(broadcast.getConfirmedByPersonId()), recipientCounts);
    }
}
