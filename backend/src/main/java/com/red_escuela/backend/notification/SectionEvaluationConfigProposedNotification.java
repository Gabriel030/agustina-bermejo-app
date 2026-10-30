package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SectionEvaluationConfigProposedNotification(Long configId, String subjectName, String sectionName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_EVAL_CONFIG_PROPOSED;
    }

    @Override
    public String message() {
        return "Nueva propuesta de configuración de evaluación pendiente de aprobación: " + subjectName + " · " + sectionName;
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
        return configId;
    }
}
