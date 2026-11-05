package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class PostWithdrawalSummaryNotificationListener {
    static final String HANDLER = "PostWithdrawalSummaryNotificationListener.deliverSummary";
    static final String RELATED_ENTITY_TYPE = "PostWithdrawalSummary";
    static final String KIND = "POST_WITHDRAWAL_SUMMARY";
    static final int DISPATCH_PRIORITY = 7;

    private final ProcessedEffectGuard effectGuard;
    private final NotificationDeliveryService deliveryService;
    private final PostWithdrawalSummaryMessageComposer composer;

    public PostWithdrawalSummaryNotificationListener(ProcessedEffectGuard effectGuard, NotificationDeliveryService deliveryService,
                                                     PostWithdrawalSummaryMessageComposer composer) {
        this.effectGuard = effectGuard;
        this.deliveryService = deliveryService;
        this.composer = composer;
    }

    @ApplicationModuleListener
    public void deliverSummary(PostWithdrawalSummaryPlannedEvent event) {
        effectGuard.runEffectOnce(event, HANDLER, () -> deliveryService.enqueue(new DeliveryRequest(envelopeOf(event), NotificationType.COLLECTION_REMINDER,
                composer.composeInApp(event), composer.composeEmail(event), false)));
    }

    static String dedupKeyOf(PostWithdrawalSummaryPlannedEvent event) {
        return "post-withdrawal-summary:" + event.studentPersonId() + ":" + event.businessDate();
    }

    private DeliveryEnvelope envelopeOf(PostWithdrawalSummaryPlannedEvent event) {
        return new DeliveryEnvelope(dedupKeyOf(event), DeliveryCategory.COLLECTION_REMINDER, KIND, DISPATCH_PRIORITY, event.studentPersonId(), RELATED_ENTITY_TYPE, null,
                event.businessDate());
    }
}
