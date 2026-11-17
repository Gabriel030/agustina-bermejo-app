package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.shared.scheduling.ScheduledJobFailureCounter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class BroadcastEmailDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(BroadcastEmailDispatchJob.class);
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);
    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);
    private static final List<DeliveryCategory> TRANSACTIONAL = List.of(DeliveryCategory.COLLECTION_REMINDER, DeliveryCategory.ANNOUNCEMENT);

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDeliveryChannel emailChannel;
    private final BroadcastConsentRecheck consentRecheck;
    private final BroadcastDispatchSettings settings;
    private final Clock clock;
    private final ScheduledJobFailureCounter failureCounter;

    public BroadcastEmailDispatchJob(NotificationDeliveryRepository deliveryRepository, EmailDeliveryChannel emailChannel,
                                     BroadcastConsentRecheck consentRecheck, BroadcastDispatchSettings settings, Clock clock,
                                     MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.emailChannel = emailChannel;
        this.consentRecheck = consentRecheck;
        this.settings = settings;
        this.clock = clock;
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
    }

    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "BroadcastEmailDispatchJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        dispatchPendingBroadcastEmails();
    }

    public int dispatchPendingBroadcastEmails() {
        if (!settings.window().isOpenAt(LocalTime.now(clock))) {
            return 0;
        }
        if (remainingQuotaToday() == 0) {
            log.debug("Cupo diario de mails masivos agotado, el resto queda para el dia siguiente");
            return 0;
        }
        Instant now = clock.instant();
        List<Long> ids = deliveryRepository.findDispatchableIdsUpTo(DeliveryChannel.EMAIL, DeliveryCategory.BROADCAST, WAITING,
                LocalDate.now(clock), now, PageRequest.of(0, settings.maxPerRun()));
        int attempted = 0;
        for (Long id : ids) {
            if (!settings.window().isOpenAt(LocalTime.now(clock)) || remainingQuotaToday() == 0) {
                break;
            }
            if (dispatchOne(id)) {
                attempted++;
            }
        }
        return attempted;
    }

    private int remainingQuotaToday() {
        LocalDate today = LocalDate.now(clock);
        Instant from = today.atStartOfDay(clock.getZone()).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        long transactionalSent = TRANSACTIONAL.stream()
                .mapToLong(category -> deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, category, SENT, from, until))
                .sum();
        long broadcastSent = deliveryRepository.countSentBetween(DeliveryChannel.EMAIL, DeliveryCategory.BROADCAST, SENT, from, until);
        return settings.quota().remaining(transactionalSent, broadcastSent);
    }

    private boolean dispatchOne(Long id) {
        try {
            if (consentRecheck.suppressIfConsentWithdrawn(id)) {
                return false;
            }
            emailChannel.dispatchDelivery(id);
            return true;
        } catch (RuntimeException failure) {
            failureCounter.record("BroadcastEmailDispatchJob", failure);
            log.warn("Fallo el despacho del mail masivo {} causa={}", id, failure.getClass().getSimpleName());
            return true;
        }
    }
}
