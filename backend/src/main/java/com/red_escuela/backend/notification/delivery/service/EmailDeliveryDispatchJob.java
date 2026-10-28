package com.red_escuela.backend.notification.delivery.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchSettings;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchWindow;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
public class EmailDeliveryDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryDispatchJob.class);
    private static final List<DeliveryStatus> WAITING = List.of(DeliveryStatus.QUEUED, DeliveryStatus.DEFERRED);
    private static final int MAX_DAYS_TO_NEXT_BUSINESS_DAY = 30;

    private final NotificationDeliveryRepository deliveryRepository;
    private final EmailDispatchPlanningService planningService;
    private final EmailDispatchSettingsSource settingsSource;
    private final EmailDeliveryChannel emailChannel;
    private final BusinessDayCalendar businessDayCalendar;
    private final Clock clock;

    public EmailDeliveryDispatchJob(NotificationDeliveryRepository deliveryRepository, EmailDispatchPlanningService planningService,
                                    EmailDispatchSettingsSource settingsSource, EmailDeliveryChannel emailChannel, BusinessDayCalendar businessDayCalendar,
                                    Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.planningService = planningService;
        this.settingsSource = settingsSource;
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
        EmailDispatchSettings settings = settingsSource.settingsEffectiveOn(today);
        EmailDispatchWindow window = settings.window();
        if (!window.isOpenAt(LocalTime.now(clock))) {
            warnIfPendingAfterWindowClosed(today, window);
            return 0;
        }
        List<Long> toSend = planningService.planAndApplyFor(today, settings, nextBusinessDayAfter(today));
        int attempted = 0;
        for (Long id : toSend) {
            if (!window.isOpenAt(LocalTime.now(clock))) {
                log.warn("La franja de envio de mails se cerro con {} mails planificados sin enviar fecha={}", toSend.size() - attempted, today);
                return attempted;
            }
            if (planningService.suppressBeforeSending(id, today)) {
                continue;
            }
            dispatchOne(id);
            attempted++;
        }
        return attempted;
    }

    private LocalDate nextBusinessDayAfter(LocalDate today) {
        LocalDate candidate = today.plusDays(1);
        for (int tried = 0; tried < MAX_DAYS_TO_NEXT_BUSINESS_DAY; tried++) {
            if (businessDayCalendar.isBusinessDay(candidate)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new IllegalStateException("There is no business day within " + MAX_DAYS_TO_NEXT_BUSINESS_DAY + " days after " + today);
    }

    private void dispatchOne(Long id) {
        try {
            emailChannel.dispatchDelivery(id);
        } catch (RuntimeException failure) {
            log.warn("Fallo el despacho de la entrega {} causa={}", id, failure.getClass().getSimpleName());
        }
    }

    private void warnIfPendingAfterWindowClosed(LocalDate today, EmailDispatchWindow window) {
        if (LocalTime.now(clock).isBefore(window.end())) {
            return;
        }
        boolean pending = !deliveryRepository.findDispatchableIds(DeliveryChannel.EMAIL, WAITING, today, clock.instant(), 0L, PageRequest.of(0, 1)).isEmpty();
        if (pending) {
            log.warn("La franja de envio de mails se cerro con entregas pendientes fecha={}", today);
        }
    }
}
