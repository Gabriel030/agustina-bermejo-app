package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record DirectorApprovalRequestApprovedEvent(DirectorApprovalRequest request) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.DIRECTOR_APPROVAL_REQUEST_APPROVED;
    }

    @Override
    public String message() {
        String message = "Dirección aprobó el pedido de " + request.describe() + ".";
        return request.hasDecisionNotes() ? message + " Notas: " + request.getDecisionNotes() : message;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return DirectorApprovalRequestRecipients.of(request);
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
