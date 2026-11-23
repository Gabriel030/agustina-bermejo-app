package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class InvitationSendingCapacity {

    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);

    private final NotificationDeliveryRepository deliveryRepository;
    private final InvitationWaveSettings settings;
    private final Clock clock;

    public InvitationSendingCapacity(NotificationDeliveryRepository deliveryRepository, InvitationWaveSettings settings, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.settings = settings;
        this.clock = clock;
    }

    public boolean hasCapacityToday(InvitationWave wave) {
        return remainingToday(wave) > 0;
    }

    public int remainingToday(InvitationWave wave) {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long transactionalSent = DeliveryCategory.TRANSACTIONAL_EMAIL.stream()
                .mapToLong(category -> deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, category, SENT, from, until))
                .sum();
        long broadcastSent = deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, DeliveryCategory.BROADCAST, SENT, from, until);
        long globalRemaining = settings.globalDailyLimit() - transactionalSent - broadcastSent;
        long waveSent = deliveryRepository.countSentForRelatedEntityBetween(DeliveryChannel.EMAIL, DeliveryCategory.INVITATION,
                InvitationDeliveryRecorder.RELATED_ENTITY_TYPE, wave.getId(), SENT, from, until);
        long waveRemaining = wave.getDailyCap() - waveSent;
        return (int) Math.max(0, Math.min(globalRemaining, waveRemaining));
    }
}
