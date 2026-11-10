package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleApprovedEvent;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleRejectedEvent;
import com.red_escuela.backend.academicstructure.events.AcademicOfferingCycleSubmittedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.domain.OfferCycleApprovedNotification;
import com.red_escuela.backend.notification.domain.OfferCycleRejectedNotification;
import com.red_escuela.backend.notification.domain.OfferCycleSubmittedNotification;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OfferCycleNotificationListener {
    static final String NOTIFY_SUBMISSION_HANDLER = "OfferCycleNotificationListener.notifySubmission";
    static final String NOTIFY_APPROVAL_HANDLER = "OfferCycleNotificationListener.notifyApproval";
    static final String NOTIFY_REJECTION_HANDLER = "OfferCycleNotificationListener.notifyRejection";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public OfferCycleNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifySubmission(AcademicOfferingCycleSubmittedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_SUBMISSION_HANDLER, () -> eventPublisher.publishEvent(
                new OfferCycleSubmittedNotification(event.cycleId(), event.targetName(), event.termName())));
    }

    @ApplicationModuleListener
    public void notifyApproval(AcademicOfferingCycleApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVAL_HANDLER, () -> eventPublisher.publishEvent(
                new OfferCycleApprovedNotification(event.cycleId(), event.targetName(), event.termName(), submitterOf(event.submitterPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyRejection(AcademicOfferingCycleRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTION_HANDLER, () -> eventPublisher.publishEvent(
                new OfferCycleRejectedNotification(event.cycleId(), event.targetName(), event.termName(), event.rejectionReason(),
                        submitterOf(event.submitterPersonId()))));
    }

    private Person submitterOf(Long personId) {
        return entityManager.getReference(Person.class, personId);
    }
}
