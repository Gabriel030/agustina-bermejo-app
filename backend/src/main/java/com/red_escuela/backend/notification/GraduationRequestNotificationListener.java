package com.red_escuela.backend.notification;

import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestedEvent;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class GraduationRequestNotificationListener {
    static final String NOTIFY_APPROVED_HANDLER = "GraduationRequestNotificationListener.notifyApproved";
    static final String NOTIFY_REJECTED_HANDLER = "GraduationRequestNotificationListener.notifyRejected";
    static final String NOTIFY_CREATED_HANDLER = "GraduationRequestNotificationListener.notifyCreated";
    static final String NOTIFY_PENDING_REVIEW_HANDLER = "GraduationRequestNotificationListener.notifyPendingReview";
    static final String NOTIFY_VERIFIED_HANDLER = "GraduationRequestNotificationListener.notifyVerified";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public GraduationRequestNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyApproved(GraduationRequestApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVED_HANDLER, () -> eventPublisher.publishEvent(new GraduationRequestApprovedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.programName())));
    }

    @ApplicationModuleListener
    public void notifyRejected(GraduationRequestRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTED_HANDLER, () -> eventPublisher.publishEvent(new GraduationRequestRejectedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.rejectionReason())));
    }

    @ApplicationModuleListener
    public void notifyCreated(GraduationRequestedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_CREATED_HANDLER, () -> eventPublisher.publishEvent(new GraduationRequestCreatedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.programName())));
    }

    @ApplicationModuleListener
    public void notifyPendingReview(GraduationRequestedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_PENDING_REVIEW_HANDLER, () -> eventPublisher.publishEvent(
                new GraduationRequestPendingReviewNotification(event.requestId(), event.studentName(), event.programName())));
    }

    @ApplicationModuleListener
    public void notifyVerified(GraduationRequestVerifiedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_VERIFIED_HANDLER, () -> eventPublisher.publishEvent(new GraduationRequestVerifiedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.programName())));
    }
}
