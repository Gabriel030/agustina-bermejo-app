package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateRenderer;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.exception.TemplateSubjectRequiredException;
import org.springframework.stereotype.Component;

@Component
class MessageTemplateContentValidator {

    void validateContent(MessageTemplateRequest request) {
        if (request.channel() == ChannelType.EMAIL && (request.subject() == null || request.subject().isBlank())) {
            throw new TemplateSubjectRequiredException();
        }
        TemplateRenderer.validateVariables(request.subject(), request.bodyText());
    }
}
