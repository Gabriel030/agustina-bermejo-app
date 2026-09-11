package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.identity.entity.Person;

import java.util.List;

public record DirectorApprovalRequestApprovedEvent(DirectorApprovalRequest request, Person requestedBy) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.DIRECTOR_APPROVAL_REQUEST_APPROVED;
    }

    @Override
    public String message() {
        return "Se aprobó la excepción que solicitaste: " + request.getJustification();
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(requestedBy));
    }

    @Override
    public String relatedEntityType() {
        return "DirectorApprovalRequest";
    }

    @Override
    public Long relatedEntityId() {
        return request.getId();
    }
}
