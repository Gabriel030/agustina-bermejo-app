package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.NotificationType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.SectionEvaluationConfig;

import java.util.List;

public record SectionEvaluationConfigApprovedEvent(SectionEvaluationConfig config, Person instructor) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_APPROVED;
    }

    @Override
    public String message() {
        return "Tu configuración de evaluación fue aprobada: "
                + config.getSubjectSection().getSubject().getName() + " · " + config.getSubjectSection().getName();
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
