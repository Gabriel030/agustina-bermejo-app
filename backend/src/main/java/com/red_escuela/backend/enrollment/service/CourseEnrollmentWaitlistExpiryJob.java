package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CourseEnrollmentWaitlistExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(CourseEnrollmentWaitlistExpiryJob.class);

    private final CourseRepository courseRepository;
    private final CourseWaitlistExpiration courseWaitlistExpiration;

    public CourseEnrollmentWaitlistExpiryJob(CourseRepository courseRepository, CourseWaitlistExpiration courseWaitlistExpiration) {
        this.courseRepository = courseRepository;
        this.courseWaitlistExpiration = courseWaitlistExpiration;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.timezone}")
    @SchedulerLock(name = "CourseEnrollmentWaitlistExpiryJob", lockAtMostFor = "PT30M")
    public void expireWaitlists() {
        LocalDate today = LocalDate.now();
        log.info("Vencimiento de listas de espera iniciado: fecha={}", today);
        long start = System.currentTimeMillis();
        int candidates = 0;
        int processed = 0;
        int failed = 0;
        for (Course course : courseRepository.findByWaitlistDeadlineOffsetDaysIsNotNull()) {
            if (!course.waitlistDeadlineHasPassed(today)) {
                continue;
            }
            candidates++;
            try {
                courseWaitlistExpiration.expire(course.getId());
                processed++;
            } catch (RuntimeException e) {
                failed++;
                log.error("No se pudo vencer la lista de espera del curso {}", course.getId(), e);
            }
        }
        log.info("Vencimiento de listas de espera finalizado: candidatas={}, procesadas={}, fallidas={}, duracion={}ms",
                candidates, processed, failed, System.currentTimeMillis() - start);
    }
}
