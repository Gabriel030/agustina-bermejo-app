package com.red_escuela.backend.notification;

import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;

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
}
