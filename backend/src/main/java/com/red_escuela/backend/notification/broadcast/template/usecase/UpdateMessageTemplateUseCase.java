package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;

public interface UpdateMessageTemplateUseCase {
    MessageTemplateResponse updateMessageTemplate(UpdateMessageTemplateCommand command);
}
