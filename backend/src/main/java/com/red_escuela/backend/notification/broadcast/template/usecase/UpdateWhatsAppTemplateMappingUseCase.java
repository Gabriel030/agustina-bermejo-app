package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;


public interface UpdateWhatsAppTemplateMappingUseCase {
    WhatsAppTemplateMappingResponse updateMapping(Long id, WhatsAppTemplateMappingRequest request);
}
