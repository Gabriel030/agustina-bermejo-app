package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.notification.CourseWaitlistExpiredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseWaitlistExpiration {
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;

    public CourseWaitlistExpiration(CourseEnrollmentRepository courseEnrollmentRepository,
                                    CourseEnrollmentService courseEnrollmentService,
                                    ApplicationEventPublisher eventPublisher) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void expire(Long courseId) {
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED)) {
            enrollment.setStatus(EnrollmentStatus.WAITLIST_EXPIRED);
            courseEnrollmentRepository.save(enrollment);
            courseEnrollmentService.cancelModuleEnrollments(enrollment);
            eventPublisher.publishEvent(new CourseWaitlistExpiredEvent(enrollment));
        }
    }
}
