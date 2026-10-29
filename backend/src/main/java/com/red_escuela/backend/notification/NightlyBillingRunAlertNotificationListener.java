package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.NightlyBillingRunCompletedWithErrorsEvent;
import com.red_escuela.backend.billing.events.NightlyBillingRunFailedEvent;
import com.red_escuela.backend.billing.events.NightlyBillingRunMissedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NightlyBillingRunAlertNotificationListener {
    static final String FAILED_HANDLER = "NightlyBillingRunAlertNotificationListener.notifyRunFailed";
    static final String MISSED_HANDLER = "NightlyBillingRunAlertNotificationListener.notifyRunMissed";
    static final String WITH_ERRORS_HANDLER = "NightlyBillingRunAlertNotificationListener.notifyRunCompletedWithErrors";
    static final String RUN_ENTITY_TYPE = "NightlyBillingRun";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public NightlyBillingRunAlertNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyRunFailed(NightlyBillingRunFailedEvent event) {
        effectGuard.runEffectOnce(event, FAILED_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.NIGHTLY_RUN_FAILED,
                "El proceso nocturno del " + event.businessDate() + " falló en el paso " + event.failedStep() + " y se detuvo: los pasos siguientes no corrieron.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), RUN_ENTITY_TYPE, event.nightlyBillingRunId())));
    }

    @ApplicationModuleListener
    public void notifyRunMissed(NightlyBillingRunMissedEvent event) {
        effectGuard.runEffectOnce(event, MISSED_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.NIGHTLY_RUN_MISSED,
                "El proceso nocturno del " + event.businessDate() + " no corrió: no hay registro de la corrida de hoy.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), RUN_ENTITY_TYPE, null)));
    }

    @ApplicationModuleListener
    public void notifyRunCompletedWithErrors(NightlyBillingRunCompletedWithErrorsEvent event) {
        effectGuard.runEffectOnce(event, WITH_ERRORS_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.NIGHTLY_RUN_COMPLETED_WITH_ERRORS,
                "El proceso nocturno del " + event.businessDate() + " terminó con errores: revisá los pasos con omitidos o fallidos.",
                List.of(NotificationTopic.BILLING_RUN_SUPERVISION), RUN_ENTITY_TYPE, event.nightlyBillingRunId())));
    }
}
