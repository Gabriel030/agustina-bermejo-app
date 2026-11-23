package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record SectionModificationRejectedNotification(Long sectionId, String subjectName, String sectionName, String rejectionReason, Person requester) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.SECTION_MODIFICATION_REJECTED;
    }

    @Override
    public String message() {
        return "Tu pedido de cambio de horario de " + subjectName + " · " + sectionName + " fue rechazado. Motivo: " + rejectionReason;
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
