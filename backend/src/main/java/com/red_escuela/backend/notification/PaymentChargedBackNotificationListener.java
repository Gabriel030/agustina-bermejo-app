package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PaymentChargedBackEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentChargedBackNotificationListener {
    static final String ADMINISTRATION_HANDLER = "PaymentChargedBackNotificationListener.notifyAdministration";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentChargedBackNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyAdministration(PaymentChargedBackEvent event) {
        effectGuard.runEffectOnce(event, ADMINISTRATION_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.PAYMENT_CHARGED_BACK,
                "Se registró un contracargo de " + event.amount().value().currency() + " " + event.amount().value().amount() + " sobre el pago " + event.paymentId()
                        + ". La plata se descontó de la cuenta del alumno y sus facturas volvieron a quedar pendientes o vencidas.",
                List.of(NotificationTopic.PAYMENT_REVERSAL_SUPERVISION), "Payment", event.paymentId())));
    }
}
