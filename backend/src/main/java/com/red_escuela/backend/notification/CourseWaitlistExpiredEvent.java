package com.red_escuela.backend.notification;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.NotificationType;

import java.util.List;

public record CourseWaitlistExpiredEvent(CourseEnrollment enrollment) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.COURSE_WAITLIST_EXPIRED;
    }

    @Override
    public String message() {
        return "No conseguiste lugar en " + enrollment.getCourse().getName()
                + " antes del cierre de la lista de espera — tu inscripción se canceló y el pago se reembolsará.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(enrollment.getStudent().getPerson()));
    }

    @Override
    public String relatedEntityType() {
        return "CourseEnrollment";
    }

    @Override
    public Long relatedEntityId() {
        return enrollment.getId();
    }
}
