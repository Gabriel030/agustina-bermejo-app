package com.red_escuela.backend.notification;

import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;

import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GraduationRequestNotificationListener {
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public GraduationRequestNotificationListener(ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @EventListener
    public void notifyApproved(GraduationRequestApprovedEvent event) {
        eventPublisher.publishEvent(new GraduationRequestApprovedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.programName()));
    }

    @EventListener
    public void notifyRejected(GraduationRequestRejectedEvent event) {
        eventPublisher.publishEvent(new GraduationRequestRejectedNotification(
                event.requestId(), entityManager.getReference(Person.class, event.studentPersonId()), event.rejectionReason()));
    }
}
