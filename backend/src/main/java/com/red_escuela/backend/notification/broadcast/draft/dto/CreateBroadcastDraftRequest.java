package com.red_escuela.backend.notification.broadcast.draft.dto;

import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBroadcastDraftRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull ChannelType channel,
        @NotNull BroadcastCategory category,
        @NotNull @Valid SegmentSpecRequest segmentSpec,
        Long templateId,
        @Size(max = 300) String subject,
        String bodyText) {
}
