package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.events.HolidaysMissingForNextYearEvent;
import com.red_escuela.backend.academicstructure.repository.HolidayRepository;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class HolidaysMissingForNextYearReminderJob {

    static final String REMINDER_HANDLER = "HolidaysMissingForNextYearReminderJob.remindIfMissing";
    static final int WINDOW_DAYS_BEFORE_NEW_YEAR = 60;
    private static final int DAYS_PER_WEEK = 7;

    private static final Logger log = LoggerFactory.getLogger(HolidaysMissingForNextYearReminderJob.class);

    private final HolidayRepository holidayRepository;
    private final ProcessedEffectGuard effectGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public HolidaysMissingForNextYearReminderJob(HolidayRepository holidayRepository, ProcessedEffectGuard effectGuard,
                                                 ApplicationEventPublisher eventPublisher, Clock clock) {
        this.holidayRepository = holidayRepository;
        this.effectGuard = effectGuard;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "HolidaysMissingForNextYearReminderJob", lockAtMostFor = "PT30M")
    public void runDaily() {
        remindIfMissing(LocalDate.now(clock));
    }

    @Transactional
    public boolean remindIfMissing(LocalDate today) {
        int nextYear = today.getYear() + 1;
        LocalDate windowOpensOn = LocalDate.of(nextYear, 1, 1).minusDays(WINDOW_DAYS_BEFORE_NEW_YEAR);
        if (today.isBefore(windowOpensOn)) {
            return false;
        }
        if (holidayRepository.existsByDateBetween(LocalDate.of(nextYear, 1, 1), LocalDate.of(nextYear, 12, 31))) {
            return false;
        }
        int weeksSinceWindowOpened = (int) (ChronoUnit.DAYS.between(windowOpensOn, today) / DAYS_PER_WEEK);
        HolidaysMissingForNextYearEvent event = HolidaysMissingForNextYearEvent.missingFor(nextYear, weeksSinceWindowOpened);
        boolean published = effectGuard.runEffectOnce(event, REMINDER_HANDLER, () -> eventPublisher.publishEvent(event));
        log.info("Recordatorio de feriados faltantes: anio={}, semana={}, emitido={}", nextYear, weeksSinceWindowOpened, published);
        return published;
    }
}
