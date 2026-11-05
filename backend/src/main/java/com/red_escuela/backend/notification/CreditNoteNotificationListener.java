package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CreditNoteNotificationListener {
    static final String STUDENT_HANDLER = "CreditNoteNotificationListener.notifyStudent";
    private static final String LATE_FEE_CORRECTION_REASON = "LATE_FEE_CORRECTION";
    static final String REQUESTER_HANDLER = "CreditNoteNotificationListener.notifyRequester";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public CreditNoteNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyStudent(CreditNoteIssuedEvent event) {
        effectGuard.runEffectOnce(event, STUDENT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.CREDIT_NOTE_ISSUED, studentMessageOf(event),
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.studentPersonId()))), "CreditNote", event.creditNoteId())));
    }

    @ApplicationModuleListener
    public void notifyRequester(CreditNoteIssuedEvent event) {
        if (event.requestedByPersonId() == null) {
            return;
        }
        effectGuard.runEffectOnce(event, REQUESTER_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.CREDIT_NOTE_ISSUED,
                "La nota de crédito " + event.creditNoteNumber() + " que pediste sobre la factura " + event.invoiceNumber() + " quedó emitida.",
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.requestedByPersonId()))), "CreditNote", event.creditNoteId())));
    }

    private static String studentMessageOf(CreditNoteIssuedEvent event) {
        if (LATE_FEE_CORRECTION_REASON.equals(event.reason())) {
            return "Corregimos el recargo por mora " + event.invoiceNumber() + " por los días posteriores a tu baja: se emitió la nota de crédito "
                    + event.creditNoteNumber() + " por " + event.currency() + " " + event.totalAmount() + ".";
        }
        return "Se emitió la nota de crédito " + event.creditNoteNumber() + " por " + event.currency() + " " + event.totalAmount() + " sobre la factura "
                + event.invoiceNumber() + ".";
    }
}
