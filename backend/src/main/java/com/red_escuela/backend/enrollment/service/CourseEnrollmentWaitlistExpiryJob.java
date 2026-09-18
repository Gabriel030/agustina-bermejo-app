package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.notification.CourseWaitlistExpiredEvent;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
                eventPublisher.publishEvent(new CourseWaitlistExpiredEvent(enrollment));
            }
        }
    }
}
