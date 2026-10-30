package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CourseEnrollmentWaitlistExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentWaitlistExpiryJob.class);

    private final CourseCatalogFinder courseCatalogFinder;
    private final CourseWaitlistExpiration courseWaitlistExpiration;
    private final Clock clock;

    public CourseEnrollmentWaitlistExpiryJob(CourseCatalogFinder courseCatalogFinder, CourseWaitlistExpiration courseWaitlistExpiration, Clock clock) {
        this.courseCatalogFinder = courseCatalogFinder;
        this.courseWaitlistExpiration = courseWaitlistExpiration;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "CourseEnrollmentWaitlistExpiryJob", lockAtMostFor = "PT30M")
    public void expireWaitlists() {
        LocalDate today = LocalDate.now(clock);
        log.info("Vencimiento de listas de espera iniciado: fecha={}", today);
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        for (Long courseId : courseCatalogFinder.findCourseIdsWithWaitlistDeadlinePassed(today)) {
            candidates++;
            try {
                courseWaitlistExpiration.expire(courseId);
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.error("No se pudo vencer la lista de espera del curso {}", courseId, e);
            }
        }
        log.info("Vencimiento de listas de espera finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                candidates, processed, failed, System.currentTimeMillis() - start);
    }
}
