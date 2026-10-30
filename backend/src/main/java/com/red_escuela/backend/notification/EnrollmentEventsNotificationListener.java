package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.events.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestRejectedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentEventsNotificationListener {
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public EnrollmentEventsNotificationListener(ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @EventListener
    public void notifyWaitlistExpiration(CourseWaitlistExpiredEvent event) {
        eventPublisher.publishEvent(new CourseWaitlistExpiredNotification(event.enrollmentId(), personOf(event.studentPersonId()), event.courseName()));
    }

    @EventListener
    public void notifyDirectorApproval(DirectorApprovalRequestApprovedEvent event) {
        eventPublisher.publishEvent(new DirectorApprovalRequestApprovedNotification(event.requestId(), event.description(), event.decisionNotes(),
                personOf(event.studentPersonId()), personOf(event.requestedByPersonId())));
    }

    @EventListener
    public void notifyDirectorRejection(DirectorApprovalRequestRejectedEvent event) {
        eventPublisher.publishEvent(new DirectorApprovalRequestRejectedNotification(event.requestId(), event.description(), event.decisionNotes(),
                personOf(event.studentPersonId()), personOf(event.requestedByPersonId())));
    }

    @EventListener
    public void notifyFeeExemptionGranted(EnrollmentFeeExemptionGrantedEvent event) {
        eventPublisher.publishEvent(new EnrollmentFeeExemptionGrantedNotification(event.programEnrollmentId(), personOf(event.studentPersonId())));
    }

    @EventListener
    public void notifyFeeExemptionRevoked(EnrollmentFeeExemptionRevokedEvent event) {
        eventPublisher.publishEvent(new EnrollmentFeeExemptionRevokedNotification(event.programEnrollmentId(), personOf(event.studentPersonId())));
    }

    private Person personOf(Long personId) {
        return personId == null ? null : entityManager.getReference(Person.class, personId);
    }
}
