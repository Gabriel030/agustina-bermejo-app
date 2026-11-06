package com.red_escuela.backend.notification.delivery.service;

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
import java.time.LocalDate;
import java.util.List;

@Component
public class AnnouncementEmailDispatchJob {

    public static final int MAX_DISPATCHES_PER_RUN = 200;

    private static final Logger log = LoggerFactory.getLogger(AnnouncementEmailDispatchJob.class);
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDeliveryChannel emailChannel;
    private final Clock clock;
    private final ScheduledJobFailureCounter failureCounter;

    public AnnouncementEmailDispatchJob(NotificationDeliveryRepository deliveryRepository, EmailDeliveryChannel emailChannel, Clock clock,
                                        MeterRegistry meterRegistry) {
        this.deliveryRepository = deliveryRepository;
        this.emailChannel = emailChannel;
        this.clock = clock;
        this.failureCounter = new ScheduledJobFailureCounter(meterRegistry);
    }

    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "AnnouncementEmailDispatchJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        dispatchPendingAnnouncementEmails();
    }

    public int dispatchPendingAnnouncementEmails() {
        LocalDate today = LocalDate.now(clock);
        int attempted = 0;
        for (LocalDate scheduledFor : List.of(today.minusDays(1), today)) {
            List<Long> ids = deliveryRepository.findDispatchableIds(DeliveryChannel.EMAIL, DeliveryCategory.ANNOUNCEMENT, WAITING, scheduledFor,
                    clock.instant(), 0L, PageRequest.of(0, MAX_DISPATCHES_PER_RUN - attempted));
            for (Long id : ids) {
                dispatchOne(id);
                attempted++;
            }
            if (attempted >= MAX_DISPATCHES_PER_RUN) {
                break;
            }
        }
        return attempted;
    }

    private void dispatchOne(Long id) {
        try {
            emailChannel.dispatchDelivery(id);
        } catch (RuntimeException failure) {
            failureCounter.record("AnnouncementEmailDispatchJob", failure);
            log.warn("Fallo el despacho del mail de aviso {} causa={}", id, failure.getClass().getSimpleName());
        }
    }
}
