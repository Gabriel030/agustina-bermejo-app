package com.red_escuela.backend.notification;

import com.red_escuela.backend.enrollment.events.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestApprovedEvent;
import com.red_escuela.backend.enrollment.events.DirectorApprovalRequestRejectedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionGrantedEvent;
import com.red_escuela.backend.enrollment.events.EnrollmentFeeExemptionRevokedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentEventsNotificationListener {
    static final String NOTIFY_WAITLIST_EXPIRATION_HANDLER = "EnrollmentEventsNotificationListener.notifyWaitlistExpiration";
    static final String NOTIFY_DIRECTOR_APPROVAL_HANDLER = "EnrollmentEventsNotificationListener.notifyDirectorApproval";
    static final String NOTIFY_DIRECTOR_REJECTION_HANDLER = "EnrollmentEventsNotificationListener.notifyDirectorRejection";
    static final String NOTIFY_FEE_EXEMPTION_GRANTED_HANDLER = "EnrollmentEventsNotificationListener.notifyFeeExemptionGranted";
    static final String NOTIFY_FEE_EXEMPTION_REVOKED_HANDLER = "EnrollmentEventsNotificationListener.notifyFeeExemptionRevoked";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public EnrollmentEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyWaitlistExpiration(CourseWaitlistExpiredEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_WAITLIST_EXPIRATION_HANDLER, () -> eventPublisher.publishEvent(new CourseWaitlistExpiredNotification(event.enrollmentId(), personOf(event.studentPersonId()), event.courseName())));
    }

    @ApplicationModuleListener
    public void notifyDirectorApproval(DirectorApprovalRequestApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_DIRECTOR_APPROVAL_HANDLER, () -> eventPublisher.publishEvent(new DirectorApprovalRequestApprovedNotification(event.requestId(), event.description(), event.decisionNotes(),
                personOf(event.studentPersonId()), personOf(event.requestedByPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyDirectorRejection(DirectorApprovalRequestRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_DIRECTOR_REJECTION_HANDLER, () -> eventPublisher.publishEvent(new DirectorApprovalRequestRejectedNotification(event.requestId(), event.description(), event.decisionNotes(),
                personOf(event.studentPersonId()), personOf(event.requestedByPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyFeeExemptionGranted(EnrollmentFeeExemptionGrantedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_FEE_EXEMPTION_GRANTED_HANDLER, () -> eventPublisher.publishEvent(new EnrollmentFeeExemptionGrantedNotification(event.programEnrollmentId(), personOf(event.studentPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyFeeExemptionRevoked(EnrollmentFeeExemptionRevokedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_FEE_EXEMPTION_REVOKED_HANDLER, () -> eventPublisher.publishEvent(new EnrollmentFeeExemptionRevokedNotification(event.programEnrollmentId(), personOf(event.studentPersonId()))));
    }

    private Person personOf(Long personId) {
        return personId == null ? null : entityManager.getReference(Person.class, personId);
    }
}
