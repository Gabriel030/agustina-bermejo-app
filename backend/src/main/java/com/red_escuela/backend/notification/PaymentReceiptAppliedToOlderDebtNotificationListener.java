package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentReceiptAppliedToOlderDebtNotificationListener {
    static final String STUDENT_HANDLER = "PaymentReceiptAppliedToOlderDebtNotificationListener.notifyStudent";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public PaymentReceiptAppliedToOlderDebtNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
                                                                EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyStudent(PaymentReceiptAppliedToOlderDebtEvent event) {
        effectGuard.runEffectOnce(event, STUDENT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PAYMENT_RECEIPT_APPLIED_TO_OLDER_DEBT,
                "Aprobamos tu comprobante, pero tenías una deuda más antigua y el pago se aplicó primero a esa deuda. La factura de "
                        + event.period() + " sigue pendiente: subí un nuevo comprobante para pagarla.",
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.payerPersonId()))),
                "Invoice", event.invoiceId())));
    }
}
