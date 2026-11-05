package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.time.YearMonth;
import java.util.List;

public record SuspensionLiftedNotification(String enrollmentEntityType, Long enrollmentId, Person student, YearMonth suspendedSince, YearMonth suspendedUntil)
        implements DomainNotificationEvent {

    @Override
    public NotificationType type() {
        return NotificationType.SUSPENSION_LIFTED;
    }

    @Override
    public String message() {
        return "Tu suspensión por mora se levantó — ya estás al día.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return enrollmentEntityType;
    }

    @Override
    public Long relatedEntityId() {
        return enrollmentId;
    }
}
