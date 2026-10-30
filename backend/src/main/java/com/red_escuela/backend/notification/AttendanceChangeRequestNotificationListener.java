package com.red_escuela.backend.notification;

import com.red_escuela.backend.attendance.events.AttendanceChangeRequestApprovedEvent;
import com.red_escuela.backend.attendance.events.AttendanceChangeRequestRejectedEvent;

import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AttendanceChangeRequestNotificationListener {
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public AttendanceChangeRequestNotificationListener(ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @EventListener
    public void notifyApproved(AttendanceChangeRequestApprovedEvent event) {
        eventPublisher.publishEvent(new AttendanceChangeRequestApprovedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.requestedByPersonId()), event.studentFullName(),
                event.classDate(), event.previousStatusLabel(), event.requestedStatusLabel()));
    }

    @EventListener
    public void notifyRejected(AttendanceChangeRequestRejectedEvent event) {
        eventPublisher.publishEvent(new AttendanceChangeRequestRejectedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.requestedByPersonId()), event.studentFullName(),
                event.classDate(), event.previousStatusLabel(), event.requestedStatusLabel()));
    }
}
