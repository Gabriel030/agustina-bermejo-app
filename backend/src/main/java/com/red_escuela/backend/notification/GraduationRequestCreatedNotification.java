package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public record GraduationRequestCreatedNotification(Long requestId, Person student, String programName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.GRADUATION_REQUEST_CREATED;
    }

    @Override
    public String message() {
        return "Tu solicitud de egreso fue registrada: " + programName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "GraduationRequest";
    }

    @Override
    public Long relatedEntityId() {
        return requestId;
    }
}
