package com.red_escuela.backend.notification.domain;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;

import java.util.List;

public record OfferCycleRejectedNotification(Long cycleId, String targetName, String termName, String rejectionReason, Person submitter)
        implements DomainNotificationEvent {
    @Override
    public NotificationType type() {
        return NotificationType.OFFER_CYCLE_REJECTED;
    }

    @Override
    public String message() {
        return "La oferta académica fue rechazada: " + targetName + " · " + termName + " — motivo: " + rejectionReason;
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
