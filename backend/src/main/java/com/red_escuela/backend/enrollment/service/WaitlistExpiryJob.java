package com.red_escuela.backend.enrollment.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Service
public class WaitlistExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(WaitlistExpiryJob.class);

    private final SectionWaitlistExpiration sectionExpiration;
    private final WaitlistExpiryReminder reminder;
    private final Clock clock;

    public WaitlistExpiryJob(SectionWaitlistExpiration sectionExpiration, WaitlistExpiryReminder reminder, Clock clock) {
        this.sectionExpiration = sectionExpiration;
        this.reminder = reminder;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "WaitlistExpiryJob", lockAtMostFor = "PT30M")
    public void run() {
        expireSectionWaitlists();
        sendExpiryReminders();
    }

    public void expireSectionWaitlists() {
        LocalDateTime now = LocalDateTime.now(clock);
        forEachIsolated("vencer lista de espera de materia", sectionExpiration.findProgramSectionIdsToExpire(now), sectionExpiration::expireProgramSection);
        forEachIsolated("vencer lista de espera de módulo", sectionExpiration.findCourseSectionIdsToExpire(now), sectionExpiration::expireCourseSection);
    }

    public void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        forEachIsolated("avisar vencimiento de materia", reminder.findProgramEnrollmentIdsToRemind(now), id -> reminder.remindProgramEnrollment(id, now));
        forEachIsolated("avisar vencimiento de módulo", reminder.findCourseSectionEnrollmentIdsToRemind(now), id -> reminder.remindCourseSectionEnrollment(id, now));
        forEachIsolated("avisar vencimiento de curso", reminder.findCourseEnrollmentIdsToRemind(now.toLocalDate()), id -> reminder.remindCourseEnrollment(id, now));
    }

    private void forEachIsolated(String action, List<Long> ids, Consumer<Long> consumer) {
        for (Long id : ids) {
            try {
                consumer.accept(id);
            } catch (RuntimeException e) {
                log.error("No se pudo {} id={}", action, id, e);
            }
        }
    }
}
