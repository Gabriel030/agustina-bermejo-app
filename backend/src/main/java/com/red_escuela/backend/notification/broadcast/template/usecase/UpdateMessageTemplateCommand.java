package com.red_escuela.backend.notification.broadcast.template.usecase;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;

public record UpdateMessageTemplateCommand(Long id, MessageTemplateRequest request) {
}
