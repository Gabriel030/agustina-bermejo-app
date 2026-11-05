package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.billing.events.PaymentReceiptRejectedEvent;
import com.red_escuela.backend.billing.events.SuspensionLiftedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class BillingEventsNotificationListener {
    static final String NOTIFY_RECEIPT_APPROVED_HANDLER = "BillingEventsNotificationListener.notifyReceiptApproved";
    static final String NOTIFY_RECEIPT_REJECTED_HANDLER = "BillingEventsNotificationListener.notifyReceiptRejected";
    static final String NOTIFY_SUSPENSION_LIFTED_HANDLER = "BillingEventsNotificationListener.notifySuspensionLifted";
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public BillingEventsNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyReceiptApproved(PaymentReceiptApprovedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_RECEIPT_APPROVED_HANDLER, () -> eventPublisher.publishEvent(new PaymentReceiptApprovedNotification(event.invoiceId(), event.invoicePeriod(), personOrNull(event.payerPersonId()))));
    }

    @ApplicationModuleListener
    public void notifyReceiptRejected(PaymentReceiptRejectedEvent event) {
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_RECEIPT_REJECTED_HANDLER, () -> eventPublisher.publishEvent(new PaymentReceiptRejectedNotification(event.invoiceId(), event.invoicePeriod(), event.rejectionReason(),
                personOrNull(event.payerPersonId()))));
    }

    @ApplicationModuleListener
    public void notifySuspensionLifted(SuspensionLiftedEvent event) {
        String entityType = event.enrollmentType() == SuspensionLiftedEvent.EnrollmentType.PROGRAM ? "ProgramEnrollment" : "CourseEnrollment";
        effectGuard.runEffectOnce(DerivedEventIdentity.of(event), NOTIFY_SUSPENSION_LIFTED_HANDLER, () -> eventPublisher.publishEvent(new SuspensionLiftedNotification(entityType, event.enrollmentId(),
                entityManager.getReference(Person.class, event.studentPersonId()), event.suspendedSince(), event.suspendedUntil())));
    }

    private Person personOrNull(Long personId) {
        return personId == null ? null : entityManager.getReference(Person.class, personId);
    }
}
