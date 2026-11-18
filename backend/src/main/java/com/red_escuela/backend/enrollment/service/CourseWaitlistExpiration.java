package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseWaitlistExpiration {
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final CourseCatalogFinder courseCatalogFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditTrail auditTrail;

    public CourseWaitlistExpiration(CourseEnrollmentRepository courseEnrollmentRepository,
                                    CourseEnrollmentService courseEnrollmentService,
                                    CourseCatalogFinder courseCatalogFinder,
                                    ApplicationEventPublisher eventPublisher, AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.courseCatalogFinder = courseCatalogFinder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void expire(Long courseId) {
        courseCatalogFinder.lockCourse(courseId);
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByCourseIdAndStatus(courseId, EnrollmentStatus.WAITLISTED)) {
            enrollment.setStatus(EnrollmentStatus.WAITLIST_EXPIRED);
            courseEnrollmentRepository.save(enrollment);
            courseEnrollmentService.cancelModuleEnrollments(enrollment);
            auditTrail.record(new AuditRecord(AuditAction.WAITLIST_EXPIRED, AuditEntityType.COURSE_ENROLLMENT, enrollment.getId(),
                    "WAITLISTED", "WAITLIST_EXPIRED", null));
            eventPublisher.publishEvent(EnrollmentNotificationEvents.waitlistExpiredOf(enrollment));
        }
    }
}
