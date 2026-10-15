package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
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
public class EmailDeliveryDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryDispatchJob.class);
    private static final int BATCH_SIZE = 50;
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDeliveryChannel emailChannel;
    private final BusinessDayCalendar businessDayCalendar;
    private final Clock clock;
    private final EmailDispatchWindow window = EmailDispatchWindow.STANDARD;

    public EmailDeliveryDispatchJob(NotificationDeliveryRepository deliveryRepository, EmailDeliveryChannel emailChannel,
                                    BusinessDayCalendar businessDayCalendar, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.emailChannel = emailChannel;
        this.businessDayCalendar = businessDayCalendar;
        this.clock = clock;
    }

    @Scheduled(cron = "0 */10 9-11 * * MON-FRI", zone = "${app.timezone}")
    @SchedulerLock(name = "EmailDeliveryDispatchJob", lockAtMostFor = "PT9M", lockAtLeastFor = "PT1M")
    public void run() {
        dispatchDueEmails();
    }

    public int dispatchDueEmails() {
        LocalDate today = LocalDate.now(clock);
        if (!businessDayCalendar.isBusinessDay(today)) {
            log.debug("Despacho de mails omitido: {} no es dia habil", today);
            return 0;
        }
        int attempted = 0;
        long afterId = 0L;
        while (true) {
            if (!window.isOpenAt(LocalTime.now(clock))) {
                warnIfPendingAfterWindowClosed(today, afterId);
                return attempted;
            }
            List<Long> ids = deliveryRepository.findDispatchableIds(DeliveryChannel.EMAIL, WAITING, today, clock.instant(), afterId,
                    PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                return attempted;
            }
            for (Long id : ids) {
                if (!window.isOpenAt(LocalTime.now(clock))) {
                    warnIfPendingAfterWindowClosed(today, id - 1);
                    return attempted;
                }
                dispatchOne(id);
                attempted++;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    private void dispatchOne(Long id) {
        try {
            emailChannel.dispatchDelivery(id);
        } catch (RuntimeException failure) {
            log.warn("Fallo el despacho de la entrega {} causa={}", id, failure.getClass().getSimpleName());
        }
    }

    private void warnIfPendingAfterWindowClosed(LocalDate today, long afterId) {
        if (LocalTime.now(clock).isBefore(window.end())) {
            return;
        }
        Instant now = clock.instant();
        boolean pending = !deliveryRepository.findDispatchableIds(DeliveryChannel.EMAIL, WAITING, today, now, afterId, PageRequest.of(0, 1)).isEmpty();
        if (pending) {
            log.warn("La franja de envio de mails se cerro con entregas pendientes fecha={}", today);
        }
    }
}
