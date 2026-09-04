package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.notification.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.CourseRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Corte de la lista de espera del cupo de Course (decisión 2026-09-03): un alumno que no
// consiguió lugar antes de course.waitlistDeadlineOffsetDays días de term.startDate pierde la
// matriculación, con reembolso — mismo patrón cron que SectionEvaluationConfigReminderJob.
@Service
public class CourseEnrollmentWaitlistExpiryJob {

    private final CourseRepository courseRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;

    public CourseEnrollmentWaitlistExpiryJob(CourseRepository courseRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                              CourseEnrollmentService courseEnrollmentService, ApplicationEventPublisher eventPublisher) {
        this.courseRepository = courseRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void expireWaitlists() {
        LocalDate today = LocalDate.now();
        for (Course course : courseRepository.findByWaitlistDeadlineOffsetDaysIsNotNull()) {
            if (!course.waitlistDeadlineHasPassed(today)) {
                continue;
            }
            for (CourseEnrollment enrollment : courseEnrollmentRepository.findByCourseIdAndStatus(course.getId(), EnrollmentStatus.WAITLISTED)) {
                enrollment.setStatus(EnrollmentStatus.WAITLIST_EXPIRED);
                courseEnrollmentRepository.save(enrollment);
                courseEnrollmentService.cancelModuleEnrollments(enrollment);
                // TODO(Fase reembolsos): acá va la reversión real del pago — no existe todavía
                // ningún mecanismo de reembolso en el sistema (sin entidad Refund, sin reversión
                // de PaymentReceipt). Decisión 2026-09-03: queda pendiente de diseño aparte, no
                // bloquea esta pieza. El aviso al alumno ya sale por acá.
                eventPublisher.publishEvent(new CourseWaitlistExpiredEvent(enrollment));
            }
        }
    }
}
