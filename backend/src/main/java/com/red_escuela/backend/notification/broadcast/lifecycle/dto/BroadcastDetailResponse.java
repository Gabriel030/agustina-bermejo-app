package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record BroadcastDetailResponse(Long id, String name, BroadcastCategory category, ChannelType channel, BroadcastStatus status,
                                      String subject, String bodyText, Instant createdAt, Instant confirmedAt,
                                      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Instant cancelledAt,
                                      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String cancellationReason,
                                      Map<BroadcastRecipientStatus, Long> recipientCounts,
                                      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String createdByName,
                                      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String confirmedByName,
                                      @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate estimatedCompletionDate) {

    public static BroadcastDetailResponse from(Broadcast broadcast, Map<BroadcastRecipientStatus, Long> recipientCounts, Map<Long, String> names,
                                             LocalDate estimatedCompletionDate) {
        return new BroadcastDetailResponse(broadcast.getId(), broadcast.getName(), broadcast.getCategory(), broadcast.getChannel(),
                broadcast.getStatus(), broadcast.getSubjectSnapshot(), broadcast.getBodySnapshot(), broadcast.getCreatedAt(),
                broadcast.getConfirmedAt(), broadcast.getCancelledAt(), broadcast.getCancellationReason(), recipientCounts,
                names.get(broadcast.getCreatedByPersonId()), names.get(broadcast.getConfirmedByPersonId()), estimatedCompletionDate);
    }
}
