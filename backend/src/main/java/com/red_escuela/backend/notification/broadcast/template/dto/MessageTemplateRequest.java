package com.red_escuela.backend.notification.broadcast.template.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull BroadcastCategory category,
        @NotNull ChannelType channel,
        @Size(max = 300) String subject,
        @NotBlank @Size(max = 5000) String bodyText) {
}
