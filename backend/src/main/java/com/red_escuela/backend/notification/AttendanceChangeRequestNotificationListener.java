package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AttendanceChangeRequestNotificationListener {
    static final String NOTIFY_APPROVED_HANDLER = "AttendanceChangeRequestNotificationListener.notifyApproved";
    static final String NOTIFY_REJECTED_HANDLER = "AttendanceChangeRequestNotificationListener.notifyRejected";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public AttendanceChangeRequestNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyApproved(AttendanceChangeRequestApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVED_HANDLER, () -> eventPublisher.publishEvent(new AttendanceChangeRequestApprovedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.requestedByPersonId()), event.studentFullName(),
                event.classDate(), event.previousStatusLabel(), event.requestedStatusLabel())));
    }

    @ApplicationModuleListener
    public void notifyRejected(AttendanceChangeRequestRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTED_HANDLER, () -> eventPublisher.publishEvent(new AttendanceChangeRequestRejectedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.requestedByPersonId()), event.studentFullName(),
                event.classDate(), event.previousStatusLabel(), event.requestedStatusLabel())));
    }
}
