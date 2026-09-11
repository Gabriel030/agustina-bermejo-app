package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.util.List;

public record SectionEvaluationConfigProposedEvent(SectionEvaluationConfig config) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_PROPOSED;
    }

    @Override
    public String message() {
        return "Nueva propuesta de configuración de evaluación pendiente de aprobación: "
                + config.getSubjectSection().getSubject().getName() + " · " + config.getSubjectSection().getName();
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(NotificationTopic.DIRECTOR_APPROVALS);
    }

    @Override
    public String relatedEntityType() {
        return "SectionEvaluationConfig";
    }

    @Override
    public Long relatedEntityId() {
        return config.getId();
    }
}
