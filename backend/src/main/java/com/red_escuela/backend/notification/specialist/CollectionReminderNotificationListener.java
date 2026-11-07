package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CollectionReminderNotificationListener {
    static final String HANDLER = "CollectionReminderNotificationListener.deliverReminder";
    static final String RELATED_ENTITY_TYPE = "CollectionReminder";

    private final ProcessedEffectGuard effectGuard;
    private final NotificationDeliveryEnqueuer deliveryService;
    private final NotificationPreferenceFinder preferenceQuery;
    private final CollectionReminderMessageComposer composer;

    public CollectionReminderNotificationListener(ProcessedEffectGuard effectGuard, NotificationDeliveryEnqueuer deliveryService,
                                                  NotificationPreferenceFinder preferenceQuery, CollectionReminderMessageComposer composer) {
        this.effectGuard = effectGuard;
        this.deliveryService = deliveryService;
        this.preferenceQuery = preferenceQuery;
        this.composer = composer;
    }

    @ApplicationModuleListener
    public void deliverReminder(CollectionReminderPlannedEvent event) {
        effectGuard.runEffectOnce(event, HANDLER, () -> deliverAll(event));
    }

    private void deliverAll(CollectionReminderPlannedEvent event) {
        List<PlannedReminderMessage> inAppMessages = event.inAppMessages();
        for (int ordinal = 0; ordinal < inAppMessages.size(); ordinal++) {
            PlannedReminderMessage message = inAppMessages.get(ordinal);
            deliveryService.enqueue(new DeliveryRequest(envelopeOf(event, message, ordinal), NotificationType.COLLECTION_REMINDER,
                    composer.composeInApp(message, event.businessDate()), null, false));
        }
        boolean preDueEmailEnabled = preferenceQuery.isPreDueReminderEmailEnabled(event.studentPersonId());
        List<PlannedReminderMessage> emailMessages = event.emailMessages();
        for (int ordinal = 0; ordinal < emailMessages.size(); ordinal++) {
            PlannedReminderMessage message = emailMessages.get(ordinal);
            PlannedReminderMessage toSend = preDueEmailEnabled ? message : messageWithoutOptedOutItems(message);
            DeliveryContent content = composer.composeEmail(toSend, event.businessDate());
            deliveryService.enqueue(new DeliveryRequest(envelopeOf(event, toSend, ordinal), NotificationType.COLLECTION_REMINDER, null, content,
                    toSend.precedesDueDate()));
        }
    }

    private PlannedReminderMessage messageWithoutOptedOutItems(PlannedReminderMessage message) {
        Optional<PlannedReminderMessage> remaining = message.withoutLinesPrecedingTheirDueDate();
        return remaining.orElse(message);
    }

    private DeliveryEnvelope envelopeOf(CollectionReminderPlannedEvent event, PlannedReminderMessage message, int ordinal) {
        String dedupKey = "collection-reminder:" + event.studentPersonId() + ":" + event.businessDate() + ":" + ordinal;
        String kind = message.format() + ":" + message.leadNoticeType();
        return new DeliveryEnvelope(dedupKey, DeliveryCategory.COLLECTION_REMINDER, kind, message.priority(), event.studentPersonId(), RELATED_ENTITY_TYPE,
                event.reminderId(), event.businessDate());
    }
}
