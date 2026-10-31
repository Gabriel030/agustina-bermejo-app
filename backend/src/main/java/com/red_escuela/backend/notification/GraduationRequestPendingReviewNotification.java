package com.red_escuela.backend.notification;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record GraduationRequestPendingReviewNotification(Long requestId, String studentName, String programName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.GRADUATION_REQUEST_PENDING_REVIEW;
    }

    @Override
    public String message() {
        return "Nueva solicitud de egreso de " + studentName + " en " + programName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(NotificationTopic.GRADUATION_SUPERVISION);
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
