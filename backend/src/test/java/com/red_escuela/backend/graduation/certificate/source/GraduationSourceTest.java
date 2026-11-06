package com.red_escuela.backend.graduation.certificate.source;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.CourseSectionOutcomeFinder;
import com.red_escuela.backend.enrollment.api.CourseSectionOutcomeView;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraduationSourceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);
    private final GraduationDebtCheck debtCheck = mock(GraduationDebtCheck.class);
    private final GraduationSnapshotFactory snapshotFactory = mock(GraduationSnapshotFactory.class);
    private final ProgramEnrollmentFinder programFinder = mock(ProgramEnrollmentFinder.class);
    private final CourseEnrollmentFinder courseFinder = mock(CourseEnrollmentFinder.class);
    private final CourseSectionOutcomeFinder outcomeFinder = mock(CourseSectionOutcomeFinder.class);
    private final ProgramGraduationSource programSource = new ProgramGraduationSource(programFinder, debtCheck, snapshotFactory);
    private final CourseGraduationSource courseSource = new CourseGraduationSource(courseFinder, outcomeFinder, debtCheck,
            snapshotFactory, clock);

    private Student student() {
        Student student = new Student();
        student.setId(7L);
        return student;
    }

    private ProgramEnrollment program(ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student());
        enrollment.setStatus(status);
        enrollment.setCompletionDate(status == ProgramEnrollmentStatus.COMPLETED ? LocalDateTime.of(2026, 7, 15, 10, 0) : null);
        when(programFinder.requireById(1L)).thenReturn(enrollment);
        return enrollment;
    }

    private void course(EnrollmentStatus status, CourseSectionOutcomeView outcome) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student());
        enrollment.setStatus(status);
        when(courseFinder.requireById(2L)).thenReturn(enrollment);
        when(outcomeFinder.outcomeOf(2L)).thenReturn(outcome);
    }

    @Test
    void notCompletedEnrollmentIsNotEligible() {
        program(ProgramEnrollmentStatus.ENROLLED);

        assertThatThrownBy(() -> programSource.requireEligible(1L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void completedEnrollmentWithDebtIsNotEligible() {
        program(ProgramEnrollmentStatus.COMPLETED);
        when(debtCheck.hasDebt(7L)).thenReturn(true);

        assertThatThrownBy(() -> programSource.requireEligible(1L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void completedEnrollmentWithoutDebtIsEligible() {
        program(ProgramEnrollmentStatus.COMPLETED);

        assertThatCode(() -> programSource.requireEligible(1L)).doesNotThrowAnyException();
    }

    @Test
    void courseWithoutActiveModulesIsNotEligible() {
        course(EnrollmentStatus.IN_PROGRESS, new CourseSectionOutcomeView(0, 0, null));

        assertThatThrownBy(() -> courseSource.requireEligible(2L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void courseBeforeTermEndIsNotEligible() {
        course(EnrollmentStatus.IN_PROGRESS, new CourseSectionOutcomeView(2, 2, LocalDate.of(2026, 11, 5)));

        assertThatThrownBy(() -> courseSource.requireEligible(2L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void withdrawnEnrollmentIsNotEligible() {
        course(EnrollmentStatus.WITHDRAWN, new CourseSectionOutcomeView(2, 2, LocalDate.of(2026, 7, 1)));

        assertThatThrownBy(() -> courseSource.requireEligible(2L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void courseWithUnapprovedModuleIsNotEligible() {
        course(EnrollmentStatus.IN_PROGRESS, new CourseSectionOutcomeView(3, 2, LocalDate.of(2026, 7, 1)));

        assertThatThrownBy(() -> courseSource.requireEligible(2L)).isInstanceOf(GraduationCertificateNotEligibleException.class);
    }

    @Test
    void finishedCourseWithAllModulesApprovedIsEligible() {
        course(EnrollmentStatus.IN_PROGRESS, new CourseSectionOutcomeView(3, 3, LocalDate.of(2026, 11, 4)));

        assertThatCode(() -> courseSource.requireEligible(2L)).doesNotThrowAnyException();
    }
}
