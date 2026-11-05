package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.events.AttendanceJustificationApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceJustificationRejectedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AttendanceJustificationNotificationListener {
    static final String NOTIFY_APPROVED_HANDLER = "AttendanceJustificationNotificationListener.notifyApproved";
    static final String NOTIFY_REJECTED_HANDLER = "AttendanceJustificationNotificationListener.notifyRejected";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public AttendanceJustificationNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyApproved(AttendanceJustificationApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_APPROVED_HANDLER, () -> eventPublisher.publishEvent(new AttendanceJustificationApprovedNotification(
                event.attendanceId(), entityManager.getReference(Person.class, event.studentPersonId()), event.classDate(), event.subjectName())));
    }

    @ApplicationModuleListener
    public void notifyRejected(AttendanceJustificationRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_REJECTED_HANDLER, () -> eventPublisher.publishEvent(new AttendanceJustificationRejectedNotification(
                event.attendanceId(), entityManager.getReference(Person.class, event.studentPersonId()), event.classDate(), event.subjectName(), event.rejectionReason())));
    }
}
