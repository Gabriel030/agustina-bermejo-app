package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentLeftAsAccountCreditNotificationListener {
    static final String STUDENT_HANDLER = "PaymentLeftAsAccountCreditNotificationListener.notifyStudent";
    static final String SECRETARIAT_HANDLER = "PaymentLeftAsAccountCreditNotificationListener.notifySecretariat";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;

    public PaymentLeftAsAccountCreditNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher,
                                                          EntityManager entityManager) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
    }

    @ApplicationModuleListener
    public void notifyStudent(PaymentLeftAsAccountCreditEvent event) {
        effectGuard.runEffectOnce(event, STUDENT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PAYMENT_LEFT_AS_ACCOUNT_CREDIT,
                "Recibimos tu pago de " + event.currency() + " " + event.amount() + ", pero la factura ya no estaba pendiente. El importe queda como saldo a favor en tu cuenta corriente.",
                List.of(new PersonRecipient(entityManager.getReference(Person.class, event.studentPersonId()))),
                "Payment", event.paymentId())));
    }

    @ApplicationModuleListener
    public void notifySecretariat(PaymentLeftAsAccountCreditEvent event) {
        effectGuard.runEffectOnce(event, SECRETARIAT_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PAYMENT_LEFT_AS_ACCOUNT_CREDIT,
                "Un pago de Mercado Pago de " + event.currency() + " " + event.amount() + " llegó sobre la factura " + event.reportedInvoiceId()
                        + " que ya no estaba pendiente; quedó como saldo a favor del alumno. Revisá si corresponde devolverlo.",
                List.of(NotificationTopic.ENROLLMENT_SECRETARIAT), "Payment", event.paymentId())));
    }
}
