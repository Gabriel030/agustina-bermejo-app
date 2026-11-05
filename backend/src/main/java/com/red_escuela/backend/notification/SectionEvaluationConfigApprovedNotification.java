package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SectionEvaluationConfigApprovedNotification(Long configId, String subjectName, String sectionName, Person instructor)
        implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_APPROVED;
    }

    @Override
    public String message() {
        return "Tu configuración de evaluación fue aprobada: " + subjectName + " · " + sectionName;
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
        return configId;
    }
}
