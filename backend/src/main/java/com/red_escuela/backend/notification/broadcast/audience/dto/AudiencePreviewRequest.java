package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public record AudiencePreviewRequest(
        @NotNull ChannelType channel,
        @NotNull BroadcastCategory category,
        @NotNull @Valid SegmentSpecRequest segmentSpec,
        Boolean excludeMissingVariables,
        @Nullable Long templateId,
        @Nullable @Size(max = 300) String subject,
        @Nullable @Size(max = 5000) String bodyText,
        @NotNull @Min(0) Integer page,
        @NotNull @Min(1) @Max(100) Integer size) {
}
