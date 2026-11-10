package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record OfferCycleSubmittedNotification(Long cycleId, String targetName, String termName) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.OFFER_CYCLE_SUBMITTED;
    }

    @Override
    public String message() {
        return "Oferta académica pendiente de aprobación: " + targetName + " · " + termName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(NotificationTopic.DIRECTOR_APPROVALS);
    }

    @Override
    public String relatedEntityType() {
        return "AcademicOfferingCycle";
    }

    @Override
    public Long relatedEntityId() {
        return cycleId;
    }
}
