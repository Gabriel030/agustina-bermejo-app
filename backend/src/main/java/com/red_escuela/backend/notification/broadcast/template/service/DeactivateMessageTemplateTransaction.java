package com.red_escuela.backend.notification.broadcast.template.service;

import com.red_escuela.backend.notification.broadcast.template.exception.MessageTemplateNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class DeactivateMessageTemplateTransaction {

    private final MessageTemplateRepository repository;
    private final Clock clock;

    public DeactivateMessageTemplateTransaction(MessageTemplateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void deactivateMessageTemplate(Long id) {
        repository.findByIdAndActiveTrue(id).orElseThrow(() -> new MessageTemplateNotFoundException(id))
                .deactivate(clock.instant());
    }
}
