package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record ProgramApplicationApprovedNotification(Long applicationId, Long studentPersonId, String programName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.PROGRAM_APPLICATION_APPROVED;
    }

    @Override
    public String message() {
        return "Aprobamos tu solicitud para " + programName + ". Pagá la matrícula de ingreso para confirmar tu inscripción.";
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonIdRecipient(studentPersonId));
    }

    @Override
    public String relatedEntityType() {
        return "ProgramEnrollmentApplication";
    }

    @Override
    public Long relatedEntityId() {
        return applicationId;
    }
}
