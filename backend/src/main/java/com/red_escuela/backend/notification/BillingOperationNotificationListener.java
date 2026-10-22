package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AuthorizationRequestExpiredEvent;
import com.red_escuela.backend.billing.events.ConfigurationChangeDecidedEvent;
import com.red_escuela.backend.billing.events.ConfigurationVersionCancelledEvent;
import com.red_escuela.backend.billing.events.AuthorizationRequestedEvent;
import com.red_escuela.backend.billing.events.DiscountQuotaExhaustedEvent;
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
    static final String CONFIGURATION_DECIDED_HANDLER = "BillingOperationNotificationListener.notifyConfigurationChangeDecided";
    static final String CONFIGURATION_CANCELLED_HANDLER = "BillingOperationNotificationListener.notifyConfigurationVersionCancelled";
    static final String DISCOUNT_QUOTA_EXHAUSTED_HANDLER = "BillingOperationNotificationListener.notifyDiscountQuotaExhausted";
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
                "Hay un pedido de autorización pendiente de decisión. Revisá el detalle en el pedido.",
                List.of(recipient), "AuthorizationRequest", event.authorizationRequestId()));
    }

    @ApplicationModuleListener
    public void notifyAuthorizationRequestExpired(AuthorizationRequestExpiredEvent event) {
        sendOnce(event, AUTHORIZATION_EXPIRED_HANDLER, new BillingOperationNotification(
                NotificationType.AUTHORIZATION_REQUEST_EXPIRED,
                "Una propuesta de configuración venció sin que se decidiera. Hay que cargarla de nuevo con una fecha nueva.",
                List.of(NotificationTopic.BILLING_CONFIGURATION_SUPERVISION), "AuthorizationRequest", event.authorizationRequestId()));
    }

    @ApplicationModuleListener
    public void notifyConfigurationChangeDecided(ConfigurationChangeDecidedEvent event) {
        boolean approved = "APPROVED".equals(event.decision());
        String message = approved ? "El director aprobó tu propuesta de configuración (" + event.topicKey() + ")."
                : "El director rechazó tu propuesta de configuración (" + event.topicKey() + "): " + event.reason();
        sendOnce(event, CONFIGURATION_DECIDED_HANDLER, new BillingOperationNotification(
                approved ? NotificationType.CONFIGURATION_CHANGE_APPROVED : NotificationType.CONFIGURATION_CHANGE_REJECTED,
                message, List.of(NotificationTopic.BILLING_CONFIGURATION_SUPERVISION), "ConfigurationVersion", event.configurationVersionId()));
    }

    @ApplicationModuleListener
    public void notifyConfigurationVersionCancelled(ConfigurationVersionCancelledEvent event) {
        sendOnce(event, CONFIGURATION_CANCELLED_HANDLER, new BillingOperationNotification(
                NotificationType.CONFIGURATION_VERSION_CANCELLED,
                "Se canceló antes de regir el cambio de configuración " + event.topicKey() + " previsto desde el " + event.effectiveFrom() + ": " + event.reason(),
                List.of(NotificationTopic.BILLING_CONFIGURATION_SUPERVISION), "ConfigurationVersion", event.configurationVersionId()));
    }

    @ApplicationModuleListener
    public void notifyDiscountQuotaExhausted(DiscountQuotaExhaustedEvent event) {
        sendOnce(event, DISCOUNT_QUOTA_EXHAUSTED_HANDLER, new BillingOperationNotification(
                NotificationType.DISCOUNT_QUOTA_EXHAUSTED,
                "Se agotó el cupo de la promoción \"" + event.discountName() + "\" (" + event.quota() + " lugares). Deja de otorgarse a inscripciones nuevas; quienes ya la tienen la conservan.",
                List.of(NotificationTopic.DISCOUNT_SUPERVISION), "DiscountDefinition", event.discountDefinitionId()));
    }

    private void sendOnce(DeduplicableDomainEvent event, String handlerName, BillingOperationNotification notification) {
        effectGuard.runEffectOnce(event, handlerName, () -> eventPublisher.publishEvent(notification));
    }
}
