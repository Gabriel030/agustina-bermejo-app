package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.LateFeeIssuedEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class LateFeeNotificationListener {
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    static final String STUDENT_HANDLER = "LateFeeNotificationListener.notifyStudent";
    static final String SECRETARIAT_HANDLER = "LateFeeNotificationListener.notifySecretariatWhenIssuedAfterFiscalWindow";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public LateFeeNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyStudent(LateFeeIssuedEvent event) {
        effectGuard.runEffectOnce(event, STUDENT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.LATE_FEE_ISSUED,
                "Se emitió la nota de débito " + event.debitNoteNumber() + " por " + event.amount().currency() + " " + event.amount().amount()
                        + " en concepto de recargo por mora del " + DAY_MONTH.format(event.periodStart()) + " al " + DAY_MONTH.format(event.periodEnd()) + " ("
                        + event.chargedDays() + " días) de la factura " + event.originInvoiceNumber() + ".",
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.studentPersonId()))), "Invoice", event.debitNoteId())));
    }

    @ApplicationModuleListener
    public void notifySecretariatWhenIssuedAfterFiscalWindow(LateFeeIssuedEvent event) {
        if (!event.issuedAfterFiscalWindow()) {
            return;
        }
        effectGuard.runEffectOnce(event, SECRETARIAT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.LATE_FEE_ISSUED_AFTER_FISCAL_WINDOW,
                "La nota de débito " + event.debitNoteNumber() + " (recargo del " + DAY_MONTH.format(event.periodStart()) + " al " + DAY_MONTH.format(event.periodEnd()) + ", "
                        + event.chargedDays() + " días, de la factura " + event.originInvoiceNumber()
                        + ") tiene fecha del hecho " + event.periodEnd() + " y se emitió el " + event.issueDate()
                        + ", fuera de los 15 días de la RG 4540. Revisala con el contador.",
                List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "Invoice", event.debitNoteId())));
    }
}
