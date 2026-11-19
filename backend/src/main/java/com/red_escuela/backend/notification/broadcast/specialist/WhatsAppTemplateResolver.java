package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariableCatalog;
import com.red_escuela.backend.notification.broadcast.template.domain.WhatsAppTemplateStatus;
import com.red_escuela.backend.notification.broadcast.template.entity.WhatsAppTemplateMapping;
import com.red_escuela.backend.notification.broadcast.template.exception.WhatsappTemplateNotApprovedException;
import com.red_escuela.backend.notification.broadcast.template.repository.WhatsAppTemplateMappingRepository;
import com.red_escuela.backend.notification.broadcast.template.specialist.TemplateVariableSource;
import com.red_escuela.backend.notification.delivery.domain.PricingCategory;
import com.red_escuela.backend.notification.delivery.domain.WhatsAppTemplateUse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class WhatsAppTemplateResolver {

    private static final String MISSING_VALUE = "-";

    private final BroadcastRepository broadcastRepository;
    private final WhatsAppTemplateMappingRepository mappingRepository;
    private final TemplateVariableSource variableSource;
    private final ObjectMapper objectMapper;

    public WhatsAppTemplateResolver(BroadcastRepository broadcastRepository, WhatsAppTemplateMappingRepository mappingRepository,
                                    TemplateVariableSource variableSource, ObjectMapper objectMapper) {
        this.broadcastRepository = broadcastRepository;
        this.mappingRepository = mappingRepository;
        this.variableSource = variableSource;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WhatsAppTemplateUse resolve(Long broadcastId, Long personId, BroadcastCategory category) {
        Long messageTemplateId = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new BroadcastNotFoundException(broadcastId))
                .getTemplateId();
        WhatsAppTemplateMapping mapping = mappingRepository.findByMessageTemplateId(messageTemplateId)
                .filter(candidate -> candidate.getStatus() == WhatsAppTemplateStatus.APPROVED)
                .orElseThrow(() -> new WhatsappTemplateNotApprovedException(messageTemplateId));
        Map<TemplateVariable, String> values = variableSource.valuesFor(personId);
        List<String> variables = mapping.getVariableOrder().stream()
                .map(token -> Objects.requireNonNullElse(values.get(TemplateVariableCatalog.findByToken(token).orElseThrow()), MISSING_VALUE))
                .toList();
        return new WhatsAppTemplateUse(mapping.getProviderTemplateName(), mapping.getLanguageCode(), toJson(variables), pricingOf(category));
    }

    private PricingCategory pricingOf(BroadcastCategory category) {
        return category == BroadcastCategory.PROMOTIONAL ? PricingCategory.MARKETING : PricingCategory.UTILITY;
    }

    private String toJson(List<String> variables) {
        return objectMapper.writeValueAsString(variables);
    }
}
