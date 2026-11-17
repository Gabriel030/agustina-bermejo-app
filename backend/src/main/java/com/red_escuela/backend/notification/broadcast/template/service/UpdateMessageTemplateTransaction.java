package com.red_escuela.backend.notification.broadcast.template.service;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import com.red_escuela.backend.notification.broadcast.template.exception.MessageTemplateNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class UpdateMessageTemplateTransaction {

    private final MessageTemplateRepository repository;
    private final Clock clock;

    public UpdateMessageTemplateTransaction(MessageTemplateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public MessageTemplateResponse updateMessageTemplate(Long id, MessageTemplateRequest request) {
        MessageTemplate template = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new MessageTemplateNotFoundException(id));
        template.rewrite(request.name().strip(), request.category(), request.channel(), request.subject(),
                request.bodyText(), clock.instant());
        return MessageTemplateResponse.from(template);
    }
}
