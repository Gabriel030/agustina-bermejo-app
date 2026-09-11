package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.util.List;

public record SectionEvaluationConfigRejectedEvent(SectionEvaluationConfig config, Person instructor) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_REJECTED;
    }

    @Override
    public String message() {
        return "Tu configuración de evaluación fue rechazada: "
                + config.getSubjectSection().getSubject().getName() + " · " + config.getSubjectSection().getName()
                + " — motivo: " + config.getRejectionReason();
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(instructor));
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
