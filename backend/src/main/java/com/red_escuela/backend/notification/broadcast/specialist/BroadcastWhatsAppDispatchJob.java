package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
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
public class BroadcastWhatsAppDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(BroadcastWhatsAppDispatchJob.class);
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);
    private static final List<DeliveryStatus> SENT = List.of(DeliveryStatus.SENT, DeliveryStatus.SENT_SIMULATED);

    private final NotificationDeliveryRepository deliveryRepository;
    private final WhatsAppDeliverySender sender;
    private final BroadcastConsentRecheck consentRecheck;
    private final BroadcastDispatchSettings settings;
    private final WhatsAppProviderSettings providerSettings;
    private final BroadcastChannelGate channelGate;
    private final Clock clock;
    private final ScheduledJobFailureCounter failureCounter;

    public BroadcastWhatsAppDispatchJob(NotificationDeliveryRepository deliveryRepository, WhatsAppDeliverySender sender,
                                        BroadcastConsentRecheck consentRecheck, BroadcastDispatchSettings settings,
                                        WhatsAppProviderSettings providerSettings, BroadcastChannelGate channelGate, Clock clock,
                                        MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.sender = sender;
        this.consentRecheck = consentRecheck;
        this.settings = settings;
        this.providerSettings = providerSettings;
        this.channelGate = channelGate;
        this.clock = clock;
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
    }

    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "BroadcastWhatsAppDispatchJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        dispatchPendingBroadcastWhatsApp();
    }

    public int dispatchPendingBroadcastWhatsApp() {
        if (!channelGate.isEnabled(ChannelType.WHATSAPP_API) || !settings.window().isOpenAt(LocalTime.now(clock))) {
            return 0;
        }
        List<Long> ids = deliveryRepository.findDispatchableIdsUpTo(DeliveryChannel.WHATSAPP_API, DeliveryCategory.BROADCAST, WAITING,
                LocalDate.now(clock), clock.instant(), PageRequest.of(0, settings.maxPerRun()));
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
        long sentToday = deliveryRepository.countSentBetween(DeliveryChannel.WHATSAPP_API, DeliveryCategory.BROADCAST, SENT, from, until);
        return (int) Math.max(0, providerSettings.dailyLimit() - sentToday);
    }

    private boolean dispatchOne(Long id) {
        try {
            if (consentRecheck.suppressIfConsentWithdrawn(id)) {
                return false;
            }
            sender.sendQueued(id);
            return true;
        } catch (RuntimeException failure) {
            failureCounter.record("BroadcastWhatsAppDispatchJob", failure);
            log.warn("Fallo el despacho del WhatsApp masivo {} causa={}", id, failure.getClass().getSimpleName());
            return true;
        }
    }
}
