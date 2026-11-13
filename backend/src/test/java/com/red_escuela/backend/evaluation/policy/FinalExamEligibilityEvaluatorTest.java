package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.attendance.specialist.ProgramSectionAbsenceLimitChecker;
import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResolver;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.exception.FinalExamBlockedByAbsencesException;
import com.red_escuela.backend.evaluation.exception.FinalExamRequiresRegularStudentException;
import com.red_escuela.backend.evaluation.exception.StudentFreeByAbsencesException;
import com.red_escuela.backend.evaluation.exception.StudentNotRegularException;
import com.red_escuela.backend.evaluation.specialist.FinalExamEligibilityEvaluator;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinalExamEligibilityEvaluatorTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final SectionEnrollmentFinder sectionEnrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final AttendanceStandingReader standingReader =
            new AttendanceStandingReader(attendanceRepository, sectionEnrollmentFinder);
    private final FinalExamEligibilityEvaluator evaluator = new FinalExamEligibilityEvaluator(
            new FinalExamEligibilityResolver(new ProgramSectionAbsenceLimitChecker(standingReader)));
    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void loadAllowsARegularStudentWithinTheAbsenceLimit() {
        SubjectSectionEnrollment enrollment = programEnrollment(1L, true, counts(1L, 8, 6, 2, 0));

        assertThatCode(() -> evaluator.requireEligibleToLoad(enrollment)).doesNotThrowAnyException();
    }

    @Test
    void loadAllowsAStudentExactlyAtTheMaximumPercentage() {
        SubjectSectionEnrollment enrollment = programEnrollment(1L, true, counts(1L, 8, 6, 2, 2));

        assertThatCode(() -> evaluator.requireEligibleToLoad(enrollment)).doesNotThrowAnyException();
    }

    @Test
    void loadRejectsAStudentFreeByAbsencesWithTheAbsenceViolation() {
        SubjectSectionEnrollment enrollment = programEnrollment(1L, true, counts(1L, 8, 5, 3, 0));

        assertThatThrownBy(() -> evaluator.requireEligibleToLoad(enrollment))
                .isInstanceOfSatisfying(FinalExamBlockedByAbsencesException.class, exception ->
                        assertThat(exception.violations()).extracting("code")
                                .containsExactly("STUDENT_FREE_BY_ABSENCES_FOR_FINAL_EXAM"));
    }

    @Test
    void loadCountsAbsencesPendingJustificationTowardsBeingFree() {
        SubjectSectionEnrollment enrollment = programEnrollment(1L, true, counts(1L, 8, 5, 3, 3));

        assertThatThrownBy(() -> evaluator.requireEligibleToLoad(enrollment))
                .isInstanceOf(FinalExamBlockedByAbsencesException.class);
    }

    @Test
    void loadRejectsANonRegularStudentWithTheRegularityViolationBeforeLookingAtAttendance() {
        SubjectSectionEnrollment enrollment = programEnrollment(1L, false, counts(1L, 8, 0, 8, 0));

        assertThatThrownBy(() -> evaluator.requireEligibleToLoad(enrollment))
                .isInstanceOfSatisfying(FinalExamRequiresRegularStudentException.class, exception ->
                        assertThat(exception.violations()).extracting("code")
                                .containsExactly("STUDENT_NOT_REGULAR_FOR_FINAL_EXAM"));
    }

    @Test
    void loadRejectsACourseEnrollmentFreeByAbsences() {
        CourseSubjectSectionEnrollment enrollment = courseEnrollment(9L, true, counts(9L, 8, 5, 3, 0));

        assertThatThrownBy(() -> evaluator.requireEligibleToLoad(enrollment))
                .isInstanceOf(FinalExamBlockedByAbsencesException.class);
    }

    @Test
    void registrationKeepsItsOwnErrorsForTheSameRules() {
        SubjectSectionEnrollment free = programEnrollment(1L, true, counts(1L, 8, 5, 3, 0));
        SubjectSectionEnrollment notRegular = programEnrollment(2L, false, counts(2L, 8, 6, 2, 0));
        SubjectSectionEnrollment fine = programEnrollment(3L, true, counts(3L, 8, 6, 2, 0));

        assertThatThrownBy(() -> evaluator.requireEligibleToRegister(free))
                .isInstanceOf(StudentFreeByAbsencesException.class);
        assertThatThrownBy(() -> evaluator.requireEligibleToRegister(notRegular))
                .isInstanceOf(StudentNotRegularException.class);
        assertThatCode(() -> evaluator.requireEligibleToRegister(fine)).doesNotThrowAnyException();
    }

    private SubjectSectionEnrollment programEnrollment(Long id, boolean regular, EnrollmentAttendanceCounts counts) {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.hasRegularCoursework()).thenReturn(regular);
        when(enrollment.getSubjectSection().getMaxAbsenceRate()).thenReturn(maxTwentyFive);
        when(sectionEnrollmentFinder.findProgramEnrollment(id)).thenReturn(enrollment);
        List<EnrollmentAttendanceCounts> rows = List.of(counts);
        when(attendanceRepository.countsByProgramEnrollmentIds(eq(List.of(id)), any(), any(), any())).thenReturn(rows);
        return enrollment;
    }

    private CourseSubjectSectionEnrollment courseEnrollment(Long id, boolean regular, EnrollmentAttendanceCounts counts) {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.hasRegularCoursework()).thenReturn(regular);
        when(enrollment.getCourseSubjectSection().getMaxAbsenceRate()).thenReturn(maxTwentyFive);
        when(sectionEnrollmentFinder.findCourseEnrollment(id)).thenReturn(enrollment);
        List<EnrollmentAttendanceCounts> rows = List.of(counts);
        when(attendanceRepository.countsByCourseEnrollmentIds(eq(List.of(id)), any(), any(), any())).thenReturn(rows);
        return enrollment;
    }

    private EnrollmentAttendanceCounts counts(Long enrollmentId, long total, long present, long absences, long pending) {
        EnrollmentAttendanceCounts counts = mock(EnrollmentAttendanceCounts.class);
        when(counts.getEnrollmentId()).thenReturn(enrollmentId);
        when(counts.getTotal()).thenReturn(total);
        when(counts.getPresent()).thenReturn(present);
        when(counts.getAbsences()).thenReturn(absences);
        when(counts.getPending()).thenReturn(pending);
        return counts;
    }
}
