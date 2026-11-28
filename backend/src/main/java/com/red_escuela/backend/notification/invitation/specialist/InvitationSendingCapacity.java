package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.delivery.domain.DailyEmailSentCounts;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.SharedDailyEmailQuota;
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
    private final SharedDailyEmailQuota sharedQuota;
    private final InvitationWaveSettings settings;
    private final Clock clock;

    public InvitationSendingCapacity(NotificationDeliveryRepository deliveryRepository, SharedDailyEmailQuota sharedQuota,
                                     InvitationWaveSettings settings, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.sharedQuota = sharedQuota;
        this.settings = settings;
        this.clock = clock;
    }

    public boolean hasCapacityToday(InvitationWave wave) {
        return remainingToday(wave) > 0;
    }

    public boolean hasCapacityTodayInSendingTurn(InvitationWave wave) {
        sharedQuota.takeSendingTurn();
        return hasCapacityToday(wave);
    }

    public int remainingToday(InvitationWave wave) {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        DailyEmailSentCounts sentToday = sharedQuota.countSentToday();
        long globalRemaining = settings.globalDailyLimit() - sentToday.transactionalSent() - sentToday.broadcastSent();
        long waveSent = deliveryRepository.countSentForRelatedEntityBetween(DeliveryChannel.EMAIL, DeliveryCategory.INVITATION,
                InvitationDeliveryRecorder.RELATED_ENTITY_TYPE, wave.getId(), SENT, from, until);
        long waveRemaining = wave.getDailyCap() - waveSent;
        return (int) Math.max(0, Math.min(globalRemaining, waveRemaining));
    }
}
