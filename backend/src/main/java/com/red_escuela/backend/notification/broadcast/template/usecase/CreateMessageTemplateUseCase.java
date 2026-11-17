package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;

public interface CreateMessageTemplateUseCase {
    MessageTemplateResponse createMessageTemplate(MessageTemplateRequest request);
}
