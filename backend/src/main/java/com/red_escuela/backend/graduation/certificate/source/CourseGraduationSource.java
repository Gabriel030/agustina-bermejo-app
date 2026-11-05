package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.CourseSectionOutcomeFinder;
import com.red_escuela.backend.enrollment.api.CourseSectionOutcomeView;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

@Component
public class CourseGraduationSource implements GraduationSource {

    private static final Set<EnrollmentStatus> REJECTED_STATUSES = Set.of(
            EnrollmentStatus.WITHDRAWN, EnrollmentStatus.WAITLISTED, EnrollmentStatus.WAITLIST_EXPIRED);

    private final CourseEnrollmentFinder courseEnrollmentFinder;
    private final CourseSectionOutcomeFinder courseSectionOutcomeFinder;
    private final GraduationDebtCheck graduationDebtCheck;
    private final GraduationSnapshotFactory snapshotFactory;
    private final Clock clock;

    public CourseGraduationSource(CourseEnrollmentFinder courseEnrollmentFinder,
                                  CourseSectionOutcomeFinder courseSectionOutcomeFinder,
                                  GraduationDebtCheck graduationDebtCheck, GraduationSnapshotFactory snapshotFactory,
                                  Clock clock) {
        this.courseEnrollmentFinder = courseEnrollmentFinder;
        this.courseSectionOutcomeFinder = courseSectionOutcomeFinder;
        this.graduationDebtCheck = graduationDebtCheck;
        this.snapshotFactory = snapshotFactory;
        this.clock = clock;
    }

    @Override
    public GraduationCertificateKind kind() {
        return GraduationCertificateKind.COURSE;
    }

    @Override
    public void requireEligible(Long enrollmentId) {
        CourseEnrollment enrollment = courseEnrollmentFinder.requireById(enrollmentId);
        if (REJECTED_STATUSES.contains(enrollment.getStatus())) {
            throw new GraduationCertificateNotEligibleException("la inscripción al curso no está vigente");
        }
        CourseSectionOutcomeView outcome = courseSectionOutcomeFinder.outcomeOf(enrollmentId);
        if (outcome.gradedSections() == 0) {
            throw new GraduationCertificateNotEligibleException("el curso no tiene módulos activos");
        }
        if (!outcome.lastTermEndDate().isBefore(LocalDate.now(clock))) {
            throw new GraduationCertificateNotEligibleException("el término del curso no finalizó");
        }
        if (!outcome.allApproved()) {
            throw new GraduationCertificateNotEligibleException("hay módulos del curso sin aprobar");
        }
        if (graduationDebtCheck.hasDebt(enrollment.getStudent().getId())) {
            throw new GraduationCertificateNotEligibleException("el alumno tiene deuda pendiente");
        }
    }

    @Override
    public CertificateSnapshot snapshotOf(Long enrollmentId) {
        CourseEnrollment enrollment = courseEnrollmentFinder.requireById(enrollmentId);
        LocalDate graduationDate = courseSectionOutcomeFinder.outcomeOf(enrollmentId).lastTermEndDate();
        return snapshotFactory.create(enrollment.getStudent(), "el curso", enrollment.getCourse().getName(), null, null,
                graduationDate);
    }

    @Override
    public Long studentIdOf(Long enrollmentId) {
        return courseEnrollmentFinder.requireById(enrollmentId).getStudent().getId();
    }

    @Override
    public Long titleIdOf(Long enrollmentId) {
        return courseEnrollmentFinder.requireById(enrollmentId).getCourse().getId();
    }
}
