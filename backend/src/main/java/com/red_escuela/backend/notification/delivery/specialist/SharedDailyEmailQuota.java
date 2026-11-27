package com.red_escuela.backend.notification.delivery.specialist;

import com.red_escuela.backend.notification.delivery.domain.DailyEmailSentCounts;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class SharedDailyEmailQuota {

    private static final long SENDING_TURN_LOCK_KEY = 933_004L;
    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);

    private final NotificationDeliveryRepository deliveryRepository;
    private final Clock clock;

    public SharedDailyEmailQuota(NotificationDeliveryRepository deliveryRepository, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    public DailyEmailSentCounts countSentToday() {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long transactionalSent = DeliveryCategory.TRANSACTIONAL_EMAIL.stream()
                .mapToLong(category -> deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, category, SENT, from, until))
                .sum();
        long broadcastSent = deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, DeliveryCategory.BROADCAST, SENT, from, until);
        return new DailyEmailSentCounts(transactionalSent, broadcastSent);
    }

    public void takeSendingTurn() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            deliveryRepository.lockDailyEmailSendingTurnUntilTransactionEnds(SENDING_TURN_LOCK_KEY);
        }
    }
}
