package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;

import java.util.List;

public interface ListWhatsAppTemplateMappingsUseCase {
    List<WhatsAppTemplateMappingResponse> listMappings();
}
