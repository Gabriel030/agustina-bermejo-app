package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;


public interface CreateWhatsAppTemplateMappingUseCase {
    WhatsAppTemplateMappingResponse createMapping(WhatsAppTemplateMappingRequest request);
}
