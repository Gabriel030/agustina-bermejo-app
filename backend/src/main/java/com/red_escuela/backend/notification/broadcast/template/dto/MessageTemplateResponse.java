package com.red_escuela.backend.notification.broadcast.template.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;

import java.time.Instant;

public record MessageTemplateResponse(Long id, String name, BroadcastCategory category, ChannelType channel,
                                      String subject, String bodyText, boolean active, Instant updatedAt) {

    public static MessageTemplateResponse from(MessageTemplate template) {
        return new MessageTemplateResponse(template.getId(), template.getName(), template.getCategory(),
                template.getChannel(), template.getSubject(), template.getBodyText(), template.isActive(),
                template.getUpdatedAt());
    }
}
