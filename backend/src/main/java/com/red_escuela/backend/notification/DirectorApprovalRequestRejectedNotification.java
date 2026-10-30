package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record DirectorApprovalRequestRejectedNotification(Long requestId, String description, String decisionNotes, Person student, Person requestedBy) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.DIRECTOR_APPROVAL_REQUEST_REJECTED;
    }

    @Override
    public String message() {
        String message = "Dirección rechazó el pedido de " + description + ".";
        return decisionNotes != null ? message + " Motivo: " + decisionNotes : message;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return DirectorApprovalRequestRecipients.of(student, requestedBy);
    }

    @Override
    public String relatedEntityType() {
        return "DirectorApprovalRequest";
    }

    @Override
    public Long relatedEntityId() {
        return requestId;
    }
}
