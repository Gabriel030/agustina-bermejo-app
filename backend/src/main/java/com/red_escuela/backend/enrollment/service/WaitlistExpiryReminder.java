package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.WaitlistExpiryReminderEvent;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WaitlistExpiryReminder {

    public static final int DAYS_BEFORE_EXPIRY = 3;

    private final SubjectSectionEnrollmentRepository programRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSectionRepository;
    private final CourseEnrollmentRepository courseRepository;
    private final CourseCatalogFinder courseCatalogFinder;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistExpiryReminder(SubjectSectionEnrollmentRepository programRepository,
                                  CourseSubjectSectionEnrollmentRepository courseSectionRepository,
                                  CourseEnrollmentRepository courseRepository, CourseCatalogFinder courseCatalogFinder,
                                  ApplicationEventPublisher eventPublisher) {
        this.programRepository = programRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.courseRepository = courseRepository;
        this.courseCatalogFinder = courseCatalogFinder;
        this.eventPublisher = eventPublisher;
    }

    public List<Long> findProgramEnrollmentIdsToRemind(LocalDateTime now) {
        return programRepository.findWaitlistedIdsToRemind(now, now.plusDays(DAYS_BEFORE_EXPIRY), EnrollmentStatus.WAITLISTED);
    }

    public List<Long> findCourseSectionEnrollmentIdsToRemind(LocalDateTime now) {
        return courseSectionRepository.findWaitlistedIdsToRemind(now, now.plusDays(DAYS_BEFORE_EXPIRY), EnrollmentStatus.WAITLISTED);
    }

    public List<Long> findCourseEnrollmentIdsToRemind(LocalDate today) {
        List<Long> courseIds = courseCatalogFinder.findCourseIdsWithWaitlistDeadlineBetween(today, today.plusDays(DAYS_BEFORE_EXPIRY));
        return courseIds.isEmpty() ? List.of() : courseRepository.findWaitlistedIdsToRemind(courseIds, EnrollmentStatus.WAITLISTED);
    }

    @Transactional
    public void remindProgramEnrollment(Long enrollmentId, LocalDateTime now) {
        SubjectSectionEnrollment enrollment = programRepository.findById(enrollmentId).orElseThrow();
        if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED || enrollment.getWaitlistExpiryRemindedAt() != null) {
            return;
        }
        enrollment.setWaitlistExpiryRemindedAt(now);
        programRepository.save(enrollment);
        eventPublisher.publishEvent(WaitlistExpiryReminderEvent.of(enrollment.getId(), enrollment.getStudent().getPerson().getId(),
                enrollment.getSubjectSection().getSubject().getName() + " - " + enrollment.getSubjectSection().getName(),
                enrollment.getSubjectSection().getTerm().getEnrollmentEnd().toLocalDate()));
    }

    @Transactional
    public void remindCourseSectionEnrollment(Long enrollmentId, LocalDateTime now) {
        CourseSubjectSectionEnrollment enrollment = courseSectionRepository.findById(enrollmentId).orElseThrow();
        if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED || enrollment.getWaitlistExpiryRemindedAt() != null) {
            return;
        }
        enrollment.setWaitlistExpiryRemindedAt(now);
        courseSectionRepository.save(enrollment);
        eventPublisher.publishEvent(WaitlistExpiryReminderEvent.of(enrollment.getId(), enrollment.getStudent().getPerson().getId(),
                enrollment.getCourseSubjectSection().getName(),
                enrollment.getCourseSubjectSection().getTerm().getEnrollmentEnd().toLocalDate()));
    }

    @Transactional
    public void remindCourseEnrollment(Long enrollmentId, LocalDateTime now) {
        CourseEnrollment enrollment = courseRepository.findById(enrollmentId).orElseThrow();
        if (enrollment.getStatus() != EnrollmentStatus.WAITLISTED || enrollment.getWaitlistExpiryRemindedAt() != null) {
            return;
        }
        enrollment.setWaitlistExpiryRemindedAt(now);
        courseRepository.save(enrollment);
        eventPublisher.publishEvent(WaitlistExpiryReminderEvent.of(enrollment.getId(), enrollment.getStudent().getPerson().getId(),
                enrollment.getCourse().getName(), enrollment.getCourse().waitlistDeadline()));
    }
}
