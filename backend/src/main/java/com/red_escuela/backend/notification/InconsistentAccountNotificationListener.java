package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.InconsistentAccountDetectedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InconsistentAccountNotificationListener {
    static final String DIRECTOR_HANDLER = "InconsistentAccountNotificationListener.notifyDirector";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public InconsistentAccountNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifyDirector(InconsistentAccountDetectedEvent event) {
        effectGuard.runEffectOnce(event, DIRECTOR_HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.INCONSISTENT_ACCOUNT_DETECTED,
                "La cuenta corriente " + event.accountId() + " no cierra (" + event.findingCode() + ": " + event.detail() + "). Quedó fuera de los procesos de la noche del "
                        + event.businessDate() + " hasta que se revise; no se corrige sola.",
                List.of(NotificationTopic.DIRECTOR_APPROVALS), "StudentCurrentAccount", event.accountId())));
    }
}
