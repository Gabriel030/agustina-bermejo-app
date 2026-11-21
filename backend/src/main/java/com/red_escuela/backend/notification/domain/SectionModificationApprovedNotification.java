package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SectionModificationApprovedNotification(Long sectionId, String subjectName, String sectionName, Person requester) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_MODIFICATION_APPROVED;
    }

    @Override
    public String message() {
        return "Tu pedido de cambio de horario de " + subjectName + " · " + sectionName + " fue aprobado";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(requester));
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
