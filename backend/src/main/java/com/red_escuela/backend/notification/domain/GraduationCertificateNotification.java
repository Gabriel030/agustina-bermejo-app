package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record GraduationCertificateNotification(NotificationType type, String message, Person student, Long certificateId) implements DomainNotificationEvent {

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "GraduationCertificate";
    }

    @Override
    public Long relatedEntityId() {
        return certificateId;
    }
}
