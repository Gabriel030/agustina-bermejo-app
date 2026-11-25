package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.academicstructure.events.SectionModificationApprovedEvent;
import com.red_escuela.backend.academicstructure.events.SectionModificationRejectedEvent;
import com.red_escuela.backend.academicstructure.events.SectionModificationRequestedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.SectionModificationApprovedNotification;
import com.red_escuela.backend.notification.domain.SectionModificationRejectedNotification;
import com.red_escuela.backend.notification.domain.SectionModificationRequestedNotification;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class SectionModificationNotificationListener {
    static final String NOTIFY_REQUEST_HANDLER = "SectionModificationNotificationListener.notifyRequest";
    static final String NOTIFY_APPROVAL_HANDLER = "SectionModificationNotificationListener.notifyApproval";
    static final String NOTIFY_REJECTION_HANDLER = "SectionModificationNotificationListener.notifyRejection";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public SectionModificationNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
                                                   EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyRequest(SectionModificationRequestedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REQUEST_HANDLER, () -> eventPublisher.publishEvent(
                new SectionModificationRequestedNotification(event.sectionId(), event.subjectName(), event.sectionName())));
    }

    @ApplicationModuleListener
    public void notifyApproval(SectionModificationApprovedEvent event) {
        if (event.requestedByPersonId() == null) {
            return;
        }
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVAL_HANDLER, () -> eventPublisher.publishEvent(
                new SectionModificationApprovedNotification(event.sectionId(), event.subjectName(), event.sectionName(),
                        requesterOf(event.requestedByPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyRejection(SectionModificationRejectedEvent event) {
        if (event.requestedByPersonId() == null) {
            return;
        }
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTION_HANDLER, () -> eventPublisher.publishEvent(
                new SectionModificationRejectedNotification(event.sectionId(), event.subjectName(), event.sectionName(),
                        event.rejectionReason(), requesterOf(event.requestedByPersonId()))));
    }

    private Person requesterOf(Long personId) {
        return entityManager.getReference(Person.class, personId);
    }
}
