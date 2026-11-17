package com.red_escuela.backend.notification.broadcast.template.service;

import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.MessageTemplateResponse;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class CreateMessageTemplateTransaction {

    private final MessageTemplateRepository repository;
    private final Clock clock;

    public CreateMessageTemplateTransaction(MessageTemplateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public MessageTemplateResponse createMessageTemplate(MessageTemplateRequest request, Long createdByPersonId) {
        MessageTemplate template = MessageTemplate.builder()
                .name(request.name().strip())
                .category(request.category())
                .channel(request.channel())
                .subject(request.subject())
                .bodyText(request.bodyText())
                .createdByPersonId(createdByPersonId)
                .createdAt(clock.instant())
                .updatedAt(clock.instant())
                .build();
        return MessageTemplateResponse.from(repository.save(template));
    }
}
