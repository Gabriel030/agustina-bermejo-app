package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.billing.events.PaymentReceiptRejectedEvent;
import com.red_escuela.backend.billing.events.SuspensionLiftedEvent;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BillingEventsNotificationListener {
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public BillingEventsNotificationListener(ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @EventListener
    public void notifyReceiptApproved(PaymentReceiptApprovedEvent event) {
        eventPublisher.publishEvent(new PaymentReceiptApprovedNotification(event.invoiceId(), event.invoicePeriod(), personOrNull(event.payerPersonId())));
    }

    @EventListener
    public void notifyReceiptRejected(PaymentReceiptRejectedEvent event) {
        eventPublisher.publishEvent(new PaymentReceiptRejectedNotification(event.invoiceId(), event.invoicePeriod(), event.rejectionReason(),
                personOrNull(event.payerPersonId())));
    }

    @EventListener
    public void notifySuspensionLifted(SuspensionLiftedEvent event) {
        String entityType = event.enrollmentType() == SuspensionLiftedEvent.EnrollmentType.PROGRAM ? "ProgramEnrollment" : "CourseEnrollment";
        eventPublisher.publishEvent(new SuspensionLiftedNotification(entityType, event.enrollmentId(),
                entityManager.getReference(Person.class, event.studentPersonId()), event.suspendedSince(), event.suspendedUntil()));
    }

    private Person personOrNull(Long personId) {
        return personId == null ? null : entityManager.getReference(Person.class, personId);
    }
}
