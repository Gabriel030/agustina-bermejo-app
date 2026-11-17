package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AudiencePreviewRequest(
        @NotNull ChannelType channel,
        @NotNull BroadcastCategory category,
        @NotNull @Valid SegmentSpecRequest segmentSpec,
        Boolean excludeMissingVariables,
        @NotNull @Min(0) Integer page,
        @NotNull @Min(1) @Max(100) Integer size) {
}
