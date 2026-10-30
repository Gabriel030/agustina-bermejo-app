package com.red_escuela.backend.enrollment.service;

import java.time.Clock;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CannotWithdrawException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CourseSectionEnrollmentService {
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;
    private final SectionFinder sectionFinder;
    private final Clock clock;

    public CourseSectionEnrollmentService(CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository,
            SectionFinder sectionFinder,
            Clock clock) {
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
        this.sectionFinder = sectionFinder;
        this.clock = clock;
    }

    public List<CourseSubjectSectionEnrollment> listByEnrollment(Long courseEnrollmentId) {
        return courseSubjectSectionEnrollmentRepository.findByCourseEnrollmentId(courseEnrollmentId);
    }

    @Transactional
    public void withdraw(Long id) {
        CourseSubjectSectionEnrollment enrollment = findOrThrow(id);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED && enrollment.getStatus() != EnrollmentStatus.IN_PROGRESS) {
            throw new CannotWithdrawException(
                    "La inscripción " + id + " no está ENROLLED/IN_PROGRESS, no se puede dar de baja");
        }
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setDroppedAt(LocalDateTime.now(clock));
        courseSubjectSectionEnrollmentRepository.save(enrollment);
        promoteNextFromWaitlist(enrollment.courseSubjectSectionId());
    }

    private void promoteNextFromWaitlist(Long courseSubjectSectionId) {
        sectionFinder.lockCourseSection(courseSubjectSectionId);
        courseSubjectSectionEnrollmentRepository
                .findFirstByCourseSubjectSectionIdAndStatusOrderByEnrolledAtAsc(courseSubjectSectionId, EnrollmentStatus.WAITLISTED)
                .ifPresent(siguiente -> {
                    siguiente.setStatus(EnrollmentStatus.ENROLLED);
                    courseSubjectSectionEnrollmentRepository.save(siguiente);
                });
    }

    private CourseSubjectSectionEnrollment findOrThrow(Long id) {
        return courseSubjectSectionEnrollmentRepository.findById(id)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(id));
    }
}
