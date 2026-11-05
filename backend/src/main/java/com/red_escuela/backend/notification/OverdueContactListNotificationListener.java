package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.OverdueContactListUpdatedEvent;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OverdueContactListNotificationListener {
    static final String HANDLER = "OverdueContactListNotificationListener.notifySecretariat";
    static final String RELATED_ENTITY_TYPE = "OverdueContactList";

    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;

    public OverdueContactListNotificationListener(ProcessedEffectGuard effectGuard, ApplicationEventPublisher eventPublisher) {
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
    }

    @ApplicationModuleListener
    public void notifySecretariat(OverdueContactListUpdatedEvent event) {
        effectGuard.runEffectOnce(event, HANDLER, () -> eventPublisher.publishEvent(new BillingOperationNotification(
                NotificationType.OVERDUE_CONTACT_LIST_UPDATED, messageOf(event.count(), event.newCount()), List.of(NotificationTopic.ENROLLMENT_SECRETARIAT),
                RELATED_ENTITY_TYPE, null)));
    }

    static String messageOf(int count, int newCount) {
        String total = count + (count == 1 ? " alumno para contactar" : " alumnos para contactar");
        if (newCount == 0) {
            return total;
        }
        return total + ", " + newCount + (newCount == 1 ? " nuevo" : " nuevos");
    }
}
