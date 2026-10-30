package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record CourseWaitlistExpiredNotification(Long enrollmentId, Person student, String courseName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.COURSE_WAITLIST_EXPIRED;
    }

    @Override
    public String message() {
        return "No conseguiste lugar en " + courseName
                + " antes del cierre de la lista de espera — tu inscripción se canceló y el pago se reembolsará.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "CourseEnrollment";
    }

    @Override
    public Long relatedEntityId() {
        return enrollmentId;
    }
}
