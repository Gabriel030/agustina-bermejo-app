package com.red_escuela.backend.notification.broadcast.template.service;

import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariableCatalog;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingRequest;
import com.red_escuela.backend.notification.broadcast.template.dto.WhatsAppTemplateMappingResponse;
import com.red_escuela.backend.notification.broadcast.template.entity.WhatsAppTemplateMapping;
import com.red_escuela.backend.notification.broadcast.template.exception.MessageTemplateNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.exception.UnknownTemplateVariableException;
import com.red_escuela.backend.notification.broadcast.template.exception.WhatsappTemplateMappingAlreadyExistsException;
import com.red_escuela.backend.notification.broadcast.template.exception.WhatsappTemplateMappingNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import com.red_escuela.backend.notification.broadcast.template.repository.WhatsAppTemplateMappingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class SaveWhatsAppTemplateMappingTransaction {

    private final WhatsAppTemplateMappingRepository repository;
    private final MessageTemplateRepository messageTemplateRepository;
    private final Clock clock;

    public SaveWhatsAppTemplateMappingTransaction(WhatsAppTemplateMappingRepository repository,
                                                  MessageTemplateRepository messageTemplateRepository, Clock clock) {
        this.repository = repository;
        this.messageTemplateRepository = messageTemplateRepository;
        this.clock = clock;
    }

    @Transactional
    public WhatsAppTemplateMappingResponse createMapping(WhatsAppTemplateMappingRequest request, Long actorPersonId) {
        requireKnownVariables(request.variableOrder());
        messageTemplateRepository.findByIdAndActiveTrue(request.messageTemplateId())
                .orElseThrow(() -> new MessageTemplateNotFoundException(request.messageTemplateId()));
        if (repository.existsByMessageTemplateId(request.messageTemplateId())) {
            throw new WhatsappTemplateMappingAlreadyExistsException(request.messageTemplateId());
        }
        WhatsAppTemplateMapping mapping = WhatsAppTemplateMapping.builder()
                .messageTemplateId(request.messageTemplateId())
                .providerTemplateName(request.providerTemplateName())
                .languageCode(request.languageCode())
                .status(request.status())
                .variableOrder(List.copyOf(request.variableOrder()))
                .updatedBy(actorPersonId)
                .updatedAt(clock.instant())
                .build();
        return WhatsAppTemplateMappingResponse.from(repository.save(mapping));
    }

    @Transactional
    public WhatsAppTemplateMappingResponse updateMapping(Long id, WhatsAppTemplateMappingRequest request, Long actorPersonId) {
        requireKnownVariables(request.variableOrder());
        WhatsAppTemplateMapping mapping = repository.findById(id).orElseThrow(() -> new WhatsappTemplateMappingNotFoundException(id));
        mapping.setProviderTemplateName(request.providerTemplateName());
        mapping.setLanguageCode(request.languageCode());
        mapping.setStatus(request.status());
        mapping.setVariableOrder(List.copyOf(request.variableOrder()));
        mapping.setUpdatedBy(actorPersonId);
        mapping.setUpdatedAt(clock.instant());
        return WhatsAppTemplateMappingResponse.from(mapping);
    }

    @Transactional(readOnly = true)
    public List<WhatsAppTemplateMappingResponse> listMappings() {
        return repository.findAll().stream().map(WhatsAppTemplateMappingResponse::from).toList();
    }

    private void requireKnownVariables(List<String> tokens) {
        tokens.forEach(token -> TemplateVariableCatalog.findByToken(token).orElseThrow(() -> new UnknownTemplateVariableException(token)));
    }
}
