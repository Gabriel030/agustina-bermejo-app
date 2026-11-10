package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record GraduationRequestOutdatedNotification(Long requestId, Person student, String studentName, String programName)
        implements DomainNotificationEvent {

    @Override
    public NotificationType type() {
        return NotificationType.GRADUATION_REQUEST_OUTDATED;
    }

    @Override
    public String message() {
        return "La solicitud de egreso de " + studentName + " en " + programName
                + " quedó desactualizada porque se corrigió una nota publicada.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student), NotificationTopic.GRADUATION_SUPERVISION);
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
