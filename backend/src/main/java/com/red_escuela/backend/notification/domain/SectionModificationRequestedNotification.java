package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SectionModificationRequestedNotification(Long sectionId, String subjectName, String sectionName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_MODIFICATION_REQUESTED;
    }

    @Override
    public String message() {
        return "Pedido de cambio de horario: " + subjectName + " · " + sectionName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(NotificationTopic.DIRECTOR_APPROVALS);
    }

    @Override
    public String relatedEntityType() {
        return "SubjectSection";
    }

    @Override
    public Long relatedEntityId() {
        return sectionId;
    }
}
