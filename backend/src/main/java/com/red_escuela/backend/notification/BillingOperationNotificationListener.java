package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AuthorizationRequestExpiredEvent;
import com.red_escuela.backend.billing.events.AuthorizationRequestedEvent;
import com.red_escuela.backend.billing.events.LatePaymentSettingsCreatedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingOperationNotificationListener {
    static final String LATE_PAYMENT_SETTINGS_HANDLER = "BillingOperationNotificationListener.notifyLatePaymentSettingsCreated";
    static final String AUTHORIZATION_REQUESTED_HANDLER = "BillingOperationNotificationListener.notifyAuthorizationRequested";
    static final String AUTHORIZATION_EXPIRED_HANDLER = "BillingOperationNotificationListener.notifyAuthorizationRequestExpired";
    private static final String DIRECTOR_ROLE = "DIRECTOR";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public BillingOperationNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyLatePaymentSettingsCreated(LatePaymentSettingsCreatedEvent event) {
        sendOnce(event, LATE_PAYMENT_SETTINGS_HANDLER, new BillingOperationNotification(
                NotificationType.LATE_PAYMENT_SETTINGS_CREATED,
                "Se cargó una nueva configuración de mora que rige desde el " + event.effectiveFrom() + ".",
                List.of(NotificationTopic.BILLING_CONFIGURATION_SUPERVISION), "LatePaymentSettings", event.latePaymentSettingsId()));
    }

    @ApplicationModuleListener
    public void notifyAuthorizationRequested(AuthorizationRequestedEvent event) {
        NotificationRecipient recipient = DIRECTOR_ROLE.equals(event.requiredRole())
                ? NotificationTopic.DIRECTOR_APPROVALS : NotificationTopic.BILLING_CONFIGURATION_SUPERVISION;
        sendOnce(event, AUTHORIZATION_REQUESTED_HANDLER, new BillingOperationNotification(
                NotificationType.AUTHORIZATION_REQUESTED,
                "Hay un pedido de autorización pendiente de decisión: " + event.reason(),
                List.of(recipient), "AuthorizationRequest", event.authorizationRequestId()));
    }

    @ApplicationModuleListener
    public void notifyAuthorizationRequestExpired(AuthorizationRequestExpiredEvent event) {
        sendOnce(event, AUTHORIZATION_EXPIRED_HANDLER, new BillingOperationNotification(
                NotificationType.AUTHORIZATION_REQUEST_EXPIRED,
                "Una propuesta de configuración venció sin que se decidiera. Hay que cargarla de nuevo con una fecha nueva.",
                List.of(NotificationTopic.BILLING_CONFIGURATION_SUPERVISION), "AuthorizationRequest", event.authorizationRequestId()));
    }

    private void sendOnce(DeduplicableDomainEvent event, String handlerName, BillingOperationNotification notification) {
        effectGuard.runEffectOnce(event, handlerName, () -> eventPublisher.publishEvent(notification));
    }
}
