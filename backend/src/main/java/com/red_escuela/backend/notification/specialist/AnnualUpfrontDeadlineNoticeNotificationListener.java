package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.billing.events.AnnualUpfrontDeadlineNoticePlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class AnnualUpfrontDeadlineNoticeNotificationListener {
    static final String HANDLER = "AnnualUpfrontDeadlineNoticeNotificationListener.deliverNotice";
    static final String RELATED_ENTITY_TYPE = "AnnualUpfrontRequest";
    static final String KIND = "ANNUAL_UPFRONT_DEADLINE_NOTICE";
    static final int DISPATCH_PRIORITY = 1;

    private final ProcessedEffectGuard effectGuard;
    private final NotificationDeliveryEnqueuer deliveryService;
    private final AnnualUpfrontDeadlineNoticeMessageComposer composer;

    public AnnualUpfrontDeadlineNoticeNotificationListener(ProcessedEffectGuard effectGuard, NotificationDeliveryEnqueuer deliveryService,
                                                           AnnualUpfrontDeadlineNoticeMessageComposer composer) {
        this.effectGuard = effectGuard;
        this.deliveryService = deliveryService;
        this.composer = composer;
    }

    @ApplicationModuleListener
    public void deliverNotice(AnnualUpfrontDeadlineNoticePlannedEvent event) {
        effectGuard.runEffectOnce(event, HANDLER, () -> deliveryService.enqueue(new DeliveryRequest(envelopeOf(event), NotificationType.COLLECTION_REMINDER,
                composer.composeInApp(event), composer.composeEmail(event), true)));
    }

    static String dedupKeyOf(Long requestId) {
        return "annual-upfront-deadline-notice:" + requestId;
    }

    private DeliveryEnvelope envelopeOf(AnnualUpfrontDeadlineNoticePlannedEvent event) {
        return new DeliveryEnvelope(dedupKeyOf(event.requestId()), DeliveryCategory.COLLECTION_REMINDER, KIND, DISPATCH_PRIORITY, event.studentPersonId(), RELATED_ENTITY_TYPE,
                event.requestId(), event.businessDate());
    }
}
