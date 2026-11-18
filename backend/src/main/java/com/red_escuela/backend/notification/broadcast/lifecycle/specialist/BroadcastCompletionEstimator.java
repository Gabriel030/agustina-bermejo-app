package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class BroadcastCompletionEstimator {

    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);
    private static final List<DeliveryCategory> TRANSACTIONAL = List.of(DeliveryCategory.COLLECTION_REMINDER, DeliveryCategory.ANNOUNCEMENT);

    private final NotificationDeliveryRepository deliveryRepository;
    private final BroadcastDispatchSettings settings;
    private final Clock clock;

    public BroadcastCompletionEstimator(NotificationDeliveryRepository deliveryRepository, BroadcastDispatchSettings settings, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.settings = settings;
        this.clock = clock;
    }

    public LocalDate estimate(BroadcastStatus status, Map<BroadcastRecipientStatus, Long> counts) {
        if (status != BroadcastStatus.CONFIRMED && status != BroadcastStatus.SENDING) {
            return null;
        }
        long pending = counts.getOrDefault(BroadcastRecipientStatus.PENDING, 0L) + counts.getOrDefault(BroadcastRecipientStatus.QUEUED, 0L);
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long transactionalSent = TRANSACTIONAL.stream()
                .mapToLong(category -> deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, category, SENT, from, until)).sum();
        long broadcastSent = deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, DeliveryCategory.BROADCAST, SENT, from, until);
        return settings.quota().completionDate(pending, today, transactionalSent, broadcastSent);
    }
}
