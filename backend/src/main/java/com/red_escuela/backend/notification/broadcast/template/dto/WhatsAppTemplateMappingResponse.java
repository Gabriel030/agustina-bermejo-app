package com.red_escuela.backend.notification.broadcast.template.dto;

import com.red_escuela.backend.notification.broadcast.template.domain.WhatsAppTemplateStatus;
import com.red_escuela.backend.notification.broadcast.template.entity.WhatsAppTemplateMapping;

import java.time.Instant;
import java.util.List;

public record WhatsAppTemplateMappingResponse(Long id, Long messageTemplateId, String providerTemplateName, String languageCode,
                                              WhatsAppTemplateStatus status, List<String> variableOrder, Long updatedBy,
                                              Instant updatedAt) {

    public static WhatsAppTemplateMappingResponse from(WhatsAppTemplateMapping mapping) {
        return new WhatsAppTemplateMappingResponse(mapping.getId(), mapping.getMessageTemplateId(), mapping.getProviderTemplateName(),
                mapping.getLanguageCode(), mapping.getStatus(), List.copyOf(mapping.getVariableOrder()), mapping.getUpdatedBy(),
                mapping.getUpdatedAt());
    }
}
