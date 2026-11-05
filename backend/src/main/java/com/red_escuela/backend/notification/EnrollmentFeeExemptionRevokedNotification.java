package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record EnrollmentFeeExemptionRevokedNotification(Long programEnrollmentId, Person student) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.ENROLLMENT_FEE_EXEMPTION_REVOKED;
    }

    @Override
    public String message() {
        return "Se revocó tu habilitación para inscribirte sin matrícula. Pagá la matrícula para poder inscribirte.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(student));
    }

    @Override
    public String relatedEntityType() {
        return "ProgramEnrollment";
    }

    @Override
    public Long relatedEntityId() {
        return programEnrollmentId;
    }
}
