package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record OfferCycleApprovedNotification(Long cycleId, String targetName, String termName, Person submitter) implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.OFFER_CYCLE_APPROVED;
    }

    @Override
    public String message() {
        return "La oferta académica fue aprobada y sus comisiones quedaron activas: " + targetName + " · " + termName;
    }

    @Override
    public List<NotificationRecipient> recipients() {
        return List.of(new PersonRecipient(submitter));
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
