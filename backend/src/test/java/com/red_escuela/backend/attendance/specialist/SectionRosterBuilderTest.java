package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceRiskReader;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.EnrollmentAttendanceCounts;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SectionRosterBuilderTest {

    private static final long SECTION_ID = 7L;

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final SectionEnrollmentFinder finder = mock(SectionEnrollmentFinder.class);
    private final SectionRosterBuilder builder = new SectionRosterBuilder(
            attendanceRepository, finder, new AttendanceRiskCalculator(new AttendanceRiskReader(null, null)));

    @Test
    void remainingAbsencesIsFloorOfAllowedMinusAbsences() {
        givenCourseSection(points(25), enrollment(1L), counts(1L, 8, 7, 1));

        RosterEntryResponse entry = onlyEntry();

        assertThat(entry.classesHeld()).isEqualTo(8);
        assertThat(entry.maxAbsenceRate().points()).isEqualByComparingTo("25");
        assertThat(entry.remainingAbsences()).isEqualTo(1);
    }

    @Test
    void remainingAbsencesRoundsAllowedAbsencesDown() {
        givenCourseSection(points(25), enrollment(1L), counts(1L, 10, 10, 0));

        assertThat(onlyEntry().remainingAbsences()).isEqualTo(2);
    }

    @Test
    void remainingAbsencesIsZeroWhenOverTheLimit() {
        givenCourseSection(points(25), enrollment(1L), counts(1L, 8, 4, 4));

        RosterEntryResponse entry = onlyEntry();

        assertThat(entry.remainingAbsences()).isZero();
        assertThat(entry.atRisk()).isTrue();
    }

    @Test
    void remainingAbsencesIsZeroAtTheLimitWithoutBeingAtRisk() {
        givenCourseSection(points(25), enrollment(1L), counts(1L, 8, 6, 2));

        RosterEntryResponse entry = onlyEntry();

        assertThat(entry.remainingAbsences()).isZero();
        assertThat(entry.atRisk()).isFalse();
    }

    @Test
    void remainingAbsencesIsNullWithoutMaxRate() {
        givenCourseSection(null, enrollment(1L), counts(1L, 8, 7, 1));

        RosterEntryResponse entry = onlyEntry();

        assertThat(entry.remainingAbsences()).isNull();
        assertThat(entry.maxAbsenceRate()).isNull();
    }

    @Test
    void remainingAbsencesIsNullWhenNoClassesHeld() {
        givenCourseSection(points(25), enrollment(1L));

        RosterEntryResponse entry = onlyEntry();

        assertThat(entry.classesHeld()).isZero();
        assertThat(entry.remainingAbsences()).isNull();
    }

    @Test
    void remainingAbsencesIsComputedPerStudent() {
        CourseSubjectSectionEnrollment first = enrollment(1L);
        CourseSubjectSectionEnrollment second = enrollment(2L);
        givenCourseSection(points(25), List.of(first, second), List.of(counts(1L, 8, 8, 0), counts(2L, 8, 7, 1)));

        List<RosterEntryResponse> roster = builder.buildForCourseSection(SECTION_ID);

        assertThat(roster).extracting(RosterEntryResponse::remainingAbsences).containsExactly(2, 1);
    }

    private RosterEntryResponse onlyEntry() {
        List<RosterEntryResponse> roster = builder.buildForCourseSection(SECTION_ID);
        assertThat(roster).hasSize(1);
        return roster.get(0);
    }

    private void givenCourseSection(Percentage maxAbsenceRate, CourseSubjectSectionEnrollment enrollment,
                                    EnrollmentAttendanceCounts... counts) {
        givenCourseSection(maxAbsenceRate, List.of(enrollment), List.of(counts));
    }

    private void givenCourseSection(Percentage maxAbsenceRate, List<CourseSubjectSectionEnrollment> enrollments,
                                    List<EnrollmentAttendanceCounts> counts) {
        CourseSubjectSection section = mock(CourseSubjectSection.class);
        when(section.getMaxAbsenceRate()).thenReturn(maxAbsenceRate);
        enrollments.forEach(enrollment -> when(enrollment.getCourseSubjectSection()).thenReturn(section));
        when(finder.findActiveWithStudentInCourseSection(SECTION_ID)).thenReturn(enrollments);
        when(attendanceRepository.countsByCourseSection(eq(SECTION_ID), any(), any())).thenReturn(counts);
    }

    private CourseSubjectSectionEnrollment enrollment(Long id) {
        Person person = mock(Person.class);
        when(person.getName()).thenReturn("Ana");
        when(person.getLastname()).thenReturn("Perez");
        when(person.getDni()).thenReturn("30111222");
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(id + 100);
        when(student.getPerson()).thenReturn(person);
        CourseSubjectSectionEnrollment enrollment = mock(CourseSubjectSectionEnrollment.class);
        when(enrollment.getId()).thenReturn(id);
        when(enrollment.getStudent()).thenReturn(student);
        return enrollment;
    }

    private EnrollmentAttendanceCounts counts(Long enrollmentId, long total, long present, long absences) {
        EnrollmentAttendanceCounts counts = mock(EnrollmentAttendanceCounts.class);
        when(counts.getEnrollmentId()).thenReturn(enrollmentId);
        when(counts.getTotal()).thenReturn(total);
        when(counts.getPresent()).thenReturn(present);
        when(counts.getAbsences()).thenReturn(absences);
        return counts;
    }

    private Percentage points(int value) {
        return Percentage.ofPoints(BigDecimal.valueOf(value));
    }
}
