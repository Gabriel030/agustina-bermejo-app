package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceStandingReaderTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final SectionEnrollmentFinder sectionEnrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final AttendanceStandingReader reader = new AttendanceStandingReader(attendanceRepository, sectionEnrollmentFinder);
    private final Percentage maxTwentyFive = Percentage.ofPoints(BigDecimal.valueOf(25));

    @Test
    void b2U01ProgramEnrollmentWithMoreAbsencesThanTheMaximumExceedsTheLimit() {
        givenProgramEnrollment(7L, maxTwentyFive, counts(7L, 8, 5, 3, 1));

        AttendanceStanding standing = reader.readProgramEnrollmentStanding(7L);

        assertThat(standing.exceedsAbsenceLimit()).isTrue();
        assertThat(standing.absencePercent().points()).isEqualByComparingTo("37.5");
        assertThat(standing.countableAbsences()).isEqualTo(3);
        assertThat(standing.pendingJustificationAbsences()).isEqualTo(1);
    }

    @Test
    void b2U02CourseEnrollmentWithAbsencesWithinTheMaximumDoesNotExceedTheLimit() {
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getCourseSubjectSection().getMaxAbsenceRate()).thenReturn(maxTwentyFive);
        when(sectionEnrollmentFinder.findCourseEnrollment(9L)).thenReturn(enrollment);
        List<EnrollmentAttendanceCounts> rows = List.of(counts(9L, 8, 6, 2, 0));
        when(attendanceRepository.countsByCourseEnrollmentIds(eq(List.of(9L)), any(), any(), any()))
                .thenReturn(rows);

        AttendanceStanding standing = reader.readCourseEnrollmentStanding(9L);

        assertThat(standing.calculable()).isTrue();
        assertThat(standing.exceedsAbsenceLimit()).isFalse();
        assertThat(standing.remainingAbsences()).isZero();
    }

    @Test
    void b2U03SectionWithoutMaximumIsNotCalculableAndNeverExceedsButKeepsItsCounts() {
        givenProgramEnrollment(3L, null, counts(3L, 8, 0, 8, 2));

        AttendanceStanding standing = reader.readProgramEnrollmentStanding(3L);

        assertThat(standing.calculable()).isFalse();
        assertThat(standing.exceedsAbsenceLimit()).isFalse();
        assertThat(standing.countableAbsences()).isEqualTo(8);
        assertThat(standing.allowedAbsences()).isNull();
    }

    @Test
    void b2U04NoClassesYetIsCalculableButHasNoPercentage() {
        givenProgramEnrollment(4L, maxTwentyFive);

        AttendanceStanding standing = reader.readProgramEnrollmentStanding(4L);

        assertThat(standing.calculable()).isTrue();
        assertThat(standing.exceedsAbsenceLimit()).isFalse();
        assertThat(standing.absencePercent()).isNull();
        assertThat(standing.allowedAbsences()).isNull();
    }

    @Test
    void b2U05AllowedAndRemainingAbsencesAreComputedPerEnrollmentOfTheBatch() {
        SubjectSectionEnrollment first = enrollmentWithMax(1L, maxTwentyFive);
        SubjectSectionEnrollment second = enrollmentWithMax(2L, Percentage.ofPoints(BigDecimal.valueOf(50)));
        when(sectionEnrollmentFinder.findProgramEnrollmentsWithSection(List.of(1L, 2L))).thenReturn(List.of(first, second));
        List<EnrollmentAttendanceCounts> rows = List.of(counts(1L, 10, 9, 1, 0), counts(2L, 10, 6, 4, 0));
        when(attendanceRepository.countsByProgramEnrollmentIds(eq(List.of(1L, 2L)), any(), any(), any()))
                .thenReturn(rows);

        Map<Long, AttendanceStanding> standings = reader.readProgramEnrollmentStandings(List.of(1L, 2L));

        assertThat(standings.get(1L).allowedAbsences()).isEqualTo(2);
        assertThat(standings.get(1L).remainingAbsences()).isEqualTo(1);
        assertThat(standings.get(2L).allowedAbsences()).isEqualTo(5);
        assertThat(standings.get(2L).remainingAbsences()).isEqualTo(1);
    }

    @Test
    void b2U06ExactlyOnTheMaximumDoesNotExceedAndOneMoreAbsenceDoes() {
        givenProgramEnrollment(5L, maxTwentyFive, counts(5L, 8, 6, 2, 0));
        assertThat(reader.readProgramEnrollmentStanding(5L).exceedsAbsenceLimit()).isFalse();

        givenProgramEnrollment(6L, maxTwentyFive, counts(6L, 8, 5, 3, 0));
        assertThat(reader.readProgramEnrollmentStanding(6L).exceedsAbsenceLimit()).isTrue();
    }

    @Test
    void b2U07PresencePercentComesFromTheSameCounts() {
        givenProgramEnrollment(8L, maxTwentyFive, counts(8L, 8, 6, 2, 0));

        assertThat(reader.readProgramEnrollmentStanding(8L).presencePercent().points()).isEqualByComparingTo("75");
    }

    private void givenProgramEnrollment(Long id, Percentage max, EnrollmentAttendanceCounts... counts) {
        SubjectSectionEnrollment enrollment = enrollmentWithMax(id, max);
        when(sectionEnrollmentFinder.findProgramEnrollment(id)).thenReturn(enrollment);
        List<EnrollmentAttendanceCounts> rows = List.of(counts);
        when(attendanceRepository.countsByProgramEnrollmentIds(eq(List.of(id)), any(), any(), any()))
                .thenReturn(rows);
    }

    private SubjectSectionEnrollment enrollmentWithMax(Long id, Percentage max) {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class, RETURNS_DEEP_STUBS);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.getSubjectSection().getMaxAbsenceRate()).thenReturn(max);
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
