package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceHistoryBuilderTest {

    private static final long SECTION_ID = 7L;
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate SECOND_DAY = LocalDate.of(2026, 3, 9);
    private static final LocalDate THIRD_DAY = LocalDate.of(2026, 3, 16);

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;
    @Mock
    private SectionEnrollmentFinder sectionEnrollmentFinder;

    private AttendanceHistoryBuilder builder() {
        return new AttendanceHistoryBuilder(attendanceRepository, classSessionRepository, sectionEnrollmentFinder);
    }

    private static Student student(long id, String name, String lastname) {
        Person person = new Person();
        person.setName(name);
        person.setLastname(lastname);
        Student student = new Student();
        student.setId(id);
        student.setPerson(person);
        return student;
    }

    private static SubjectSectionEnrollment programEnrollment(long id, Student student) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        return enrollment;
    }

    private static CourseSubjectSectionEnrollment courseEnrollment(long id, Student student) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student);
        return enrollment;
    }

    private static Attendance programAttendance(long id, SubjectSectionEnrollment enrollment, LocalDate day, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.setId(id);
        attendance.setProgramSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(day);
        attendance.setStatus(status);
        return attendance;
    }

    private static Attendance courseAttendance(long id, CourseSubjectSectionEnrollment enrollment, LocalDate day, AttendanceStatus status) {
        Attendance attendance = new Attendance();
        attendance.setId(id);
        attendance.setCourseSubjectSectionEnrollment(enrollment);
        attendance.setClassDate(day);
        attendance.setStatus(status);
        return attendance;
    }

    private static ClassSession session(LocalDate day, ClassSessionStatus status) {
        ClassSession session = new ClassSession();
        session.setClassDate(day);
        session.setStatus(status);
        return session;
    }

    @Test
    void programHistoryListsDistinctSortedDatesAndPercentagePerStudent() {
        var ana = programEnrollment(1L, student(10L, "Ana", "Perez"));
        var beto = programEnrollment(2L, student(11L, "Beto", "Gomez"));
        when(classSessionRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(SECTION_ID)).thenReturn(List.of(
                programAttendance(100L, ana, SECOND_DAY, AttendanceStatus.ABSENT),
                programAttendance(101L, ana, FIRST_DAY, AttendanceStatus.PRESENT),
                programAttendance(102L, beto, FIRST_DAY, AttendanceStatus.LATE),
                programAttendance(103L, beto, SECOND_DAY, AttendanceStatus.JUSTIFIED)));
        when(sectionEnrollmentFinder.findActiveInProgramSection(SECTION_ID)).thenReturn(List.of(beto, ana));

        AttendanceHistoryResponse history = builder().buildForProgramSection(SECTION_ID);

        assertThat(history.classDates()).containsExactly(FIRST_DAY, SECOND_DAY);
        assertThat(history.rows()).extracting(AttendanceHistoryResponse.StudentHistoryRow::studentName)
                .containsExactly("Ana Perez", "Beto Gomez");
        var anaRow = history.rows().get(0);
        assertThat(anaRow.studentId()).isEqualTo(10L);
        assertThat(anaRow.attendancePercent()).isEqualTo(Percentage.ratio(1, 2).orElseThrow());
        assertThat(anaRow.cellsByDate().get(SECOND_DAY).status()).isEqualTo("ABSENT");
        assertThat(anaRow.cellsByDate().get(SECOND_DAY).attendanceId()).isEqualTo(100L);
        assertThat(history.rows().get(1).attendancePercent()).isEqualTo(Percentage.ratio(2, 2).orElseThrow());
    }

    @Test
    void programHistoryDropsAttendanceOfCanceledClassesFromDatesAndPercentage() {
        var ana = programEnrollment(1L, student(10L, "Ana", "Perez"));
        when(classSessionRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of(
                session(SECOND_DAY, ClassSessionStatus.CANCELED), session(FIRST_DAY, ClassSessionStatus.HELD)));
        when(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(SECTION_ID)).thenReturn(List.of(
                programAttendance(100L, ana, FIRST_DAY, AttendanceStatus.PRESENT),
                programAttendance(101L, ana, SECOND_DAY, AttendanceStatus.ABSENT)));
        when(sectionEnrollmentFinder.findActiveInProgramSection(SECTION_ID)).thenReturn(List.of(ana));

        AttendanceHistoryResponse history = builder().buildForProgramSection(SECTION_ID);

        assertThat(history.classDates()).containsExactly(FIRST_DAY);
        assertThat(history.rows().get(0).cellsByDate()).containsOnlyKeys(FIRST_DAY);
        assertThat(history.rows().get(0).attendancePercent()).isEqualTo(Percentage.ratio(1, 1).orElseThrow());
    }

    @Test
    void programStudentWithoutAttendanceHasRowWithoutPercentage() {
        var ana = programEnrollment(1L, student(10L, "Ana", "Perez"));
        when(classSessionRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(SECTION_ID)).thenReturn(List.of());
        when(sectionEnrollmentFinder.findActiveInProgramSection(SECTION_ID)).thenReturn(List.of(ana));

        AttendanceHistoryResponse history = builder().buildForProgramSection(SECTION_ID);

        assertThat(history.classDates()).isEmpty();
        assertThat(history.rows()).hasSize(1);
        assertThat(history.rows().get(0).cellsByDate()).isEmpty();
        assertThat(history.rows().get(0).attendancePercent()).isNull();
    }

    @Test
    void pendingJustificationCountsAsAbsenceInTheProgramPercentage() {
        var ana = programEnrollment(1L, student(10L, "Ana", "Perez"));
        when(classSessionRepository.findBySubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_Id(SECTION_ID)).thenReturn(List.of(
                programAttendance(100L, ana, FIRST_DAY, AttendanceStatus.PRESENT),
                programAttendance(101L, ana, SECOND_DAY, AttendanceStatus.JUSTIFICATION_PENDING),
                programAttendance(102L, ana, THIRD_DAY, AttendanceStatus.JUSTIFIED)));
        when(sectionEnrollmentFinder.findActiveInProgramSection(SECTION_ID)).thenReturn(List.of(ana));

        var row = builder().buildForProgramSection(SECTION_ID).rows().get(0);

        assertThat(row.attendancePercent()).isEqualTo(Percentage.ratio(2, 3).orElseThrow());
    }

    @Test
    void courseHistoryListsDatesAndPercentagePerStudentOrderedByName() {
        var ana = courseEnrollment(1L, student(10L, "Ana", "Perez"));
        var beto = courseEnrollment(2L, student(11L, "Beto", "Gomez"));
        when(classSessionRepository.findByCourseSubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(SECTION_ID)).thenReturn(List.of(
                courseAttendance(100L, ana, SECOND_DAY, AttendanceStatus.ABSENT),
                courseAttendance(101L, ana, FIRST_DAY, AttendanceStatus.PRESENT),
                courseAttendance(102L, beto, FIRST_DAY, AttendanceStatus.PRESENT)));
        when(sectionEnrollmentFinder.findActiveInCourseSection(SECTION_ID)).thenReturn(List.of(beto, ana));

        AttendanceHistoryResponse history = builder().buildForCourseSection(SECTION_ID);

        assertThat(history.classDates()).containsExactly(FIRST_DAY, SECOND_DAY);
        assertThat(history.rows()).extracting(AttendanceHistoryResponse.StudentHistoryRow::studentName)
                .containsExactly("Ana Perez", "Beto Gomez");
        assertThat(history.rows().get(0).attendancePercent()).isEqualTo(Percentage.ratio(1, 2).orElseThrow());
        assertThat(history.rows().get(1).attendancePercent()).isEqualTo(Percentage.ratio(1, 1).orElseThrow());
    }

    @Test
    void courseHistoryDropsAttendanceOfCanceledClasses() {
        var ana = courseEnrollment(1L, student(10L, "Ana", "Perez"));
        when(classSessionRepository.findByCourseSubjectSectionId(SECTION_ID)).thenReturn(List.of(session(SECOND_DAY, ClassSessionStatus.CANCELED)));
        when(attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(SECTION_ID)).thenReturn(List.of(
                courseAttendance(100L, ana, FIRST_DAY, AttendanceStatus.ABSENT),
                courseAttendance(101L, ana, SECOND_DAY, AttendanceStatus.PRESENT)));
        when(sectionEnrollmentFinder.findActiveInCourseSection(SECTION_ID)).thenReturn(List.of(ana));

        AttendanceHistoryResponse history = builder().buildForCourseSection(SECTION_ID);

        assertThat(history.classDates()).containsExactly(FIRST_DAY);
        assertThat(history.rows().get(0).attendancePercent()).isEqualTo(Percentage.ratio(0, 1).orElseThrow());
    }

    @Test
    void courseStudentWithoutAttendanceHasRowWithoutPercentage() {
        var ana = courseEnrollment(1L, student(10L, "Ana", "Perez"));
        when(classSessionRepository.findByCourseSubjectSectionId(SECTION_ID)).thenReturn(List.of());
        when(attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_Id(SECTION_ID)).thenReturn(List.of());
        when(sectionEnrollmentFinder.findActiveInCourseSection(SECTION_ID)).thenReturn(List.of(ana));

        var row = builder().buildForCourseSection(SECTION_ID).rows().get(0);

        assertThat(row.attendancePercent()).isNull();
        assertThat(row.cellsByDate()).isEmpty();
    }
}
