package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.LateFeeAnnulledByPaymentDateEvent;
import com.red_escuela.backend.billing.events.LateFeeCorrectionIssuedAfterFiscalWindowEvent;
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
public class LateFeeCorrectionNotificationListener {
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String WITHDRAWAL_CAUSE = "BACKDATED_WITHDRAWAL";
    static final String SECRETARIAT_HANDLER = "LateFeeCorrectionNotificationListener.notifySecretariatWhenIssuedAfterFiscalWindow";
    static final String STUDENT_ANNULMENT_HANDLER = "LateFeeCorrectionNotificationListener.notifyStudentOfTheAnnulmentByPaymentDate";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public LateFeeCorrectionNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher, EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifySecretariatWhenIssuedAfterFiscalWindow(LateFeeCorrectionIssuedAfterFiscalWindowEvent event) {
        effectGuard.runEffectOnce(event, SECRETARIAT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.LATE_FEE_CORRECTION_AFTER_FISCAL_WINDOW, secretariatMessageOf(event), List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "Invoice",
                event.debitNoteId())));
    }

    @ApplicationModuleListener
    public void notifyStudentOfTheAnnulmentByPaymentDate(LateFeeAnnulledByPaymentDateEvent event) {
        effectGuard.runEffectOnce(event, STUDENT_ANNULMENT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.CREDIT_NOTE_ISSUED, studentMessageOf(event),
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.studentPersonId()))), "CreditNote", event.creditNoteId())));
    }

    private static String secretariatMessageOf(LateFeeCorrectionIssuedAfterFiscalWindowEvent event) {
        boolean withdrawal = WITHDRAWAL_CAUSE.equals(event.cause());
        return "La nota de crédito " + event.creditNoteNumber() + (withdrawal ? " (corrección del recargo de la nota de débito " : " (anulación del recargo de la nota de débito ")
                + event.debitNoteNumber() + ") se emitió el " + DAY_MONTH_YEAR.format(event.issueDate()) + (withdrawal ? ", y la baja se registró el " : ", y el pago se registró el ")
                + DAY_MONTH_YEAR.format(event.factRegisteredOn()) + ": fuera de los 15 días de la RG 4540. Revisala con el contador.";
    }

    private static String studentMessageOf(LateFeeAnnulledByPaymentDateEvent event) {
        String creditNote = " (nota de crédito " + event.creditNoteNumber() + " por " + event.currency() + " " + event.totalAmount() + ").";
        String paymentDate = DAY_MONTH_YEAR.format(event.paymentDate());
        if (event.paidWithinGrace()) {
            return "Ajustamos el recargo por mora " + event.debitNoteNumber() + ": no te cobramos " + daysOf(event.annulledDays()) + " porque pagaste el " + paymentDate
                    + ", dentro del plazo de gracia" + creditNote;
        }
        return "Ajustamos el recargo por mora " + event.debitNoteNumber() + ": no te cobramos " + daysOf(event.annulledDays())
                + (event.annulledDays() == 1 ? " posterior" : " posteriores") + " a tu pago del " + paymentDate + creditNote;
    }

    private static String daysOf(int days) {
        return days == 1 ? "el día" : "los " + days + " días";
    }
}
