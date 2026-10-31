package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentAttendanceReaderTest {

    private final AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
    private final StudentAttendanceReader reader = new StudentAttendanceReader(attendanceRepository);

    @Test
    void listsProgramAttendanceAsViewsWithTheEnrollmentId() {
        Attendance attendance = attendanceOf(programEnrollment(7L, 3L), null);
        attendance.setId(11L);
        attendance.setClassDate(LocalDate.of(2026, 3, 2));
        attendance.setStatus(AttendanceStatus.ABSENT);
        when(attendanceRepository.findByProgramSubjectSectionEnrollmentId(7L)).thenReturn(List.of(attendance));

        List<StudentAttendanceView> views = reader.listProgramEnrollmentAttendance(7L);

        assertThat(views).containsExactly(new StudentAttendanceView(
                11L, 7L, LocalDate.of(2026, 3, 2), "ABSENT", null, null, null, null));
    }

    @Test
    void listsCourseAttendanceAsViewsWithTheCourseEnrollmentId() {
        Attendance attendance = attendanceOf(null, courseEnrollment(8L, 3L));
        attendance.setId(12L);
        attendance.setClassDate(LocalDate.of(2026, 3, 9));
        when(attendanceRepository.findByCourseSubjectSectionEnrollmentId(8L)).thenReturn(List.of(attendance));

        assertThat(reader.listCourseEnrollmentAttendance(8L)).containsExactly(new StudentAttendanceView(
                12L, 8L, LocalDate.of(2026, 3, 9), "ABSENT", null, null, null));
    }

    @Test
    void acceptsAnAttendanceOfTheStudentForProgramAndCourseEnrollments() {
        Attendance program = attendanceOf(programEnrollment(7L, 3L), null);
        Attendance course = attendanceOf(null, courseEnrollment(8L, 3L));
        when(attendanceRepository.findById(1L)).thenReturn(Optional.of(program));
        when(attendanceRepository.findById(2L)).thenReturn(Optional.of(course));

        assertThatCode(() -> reader.requireAttendanceOwnedBy(1L, 3L)).doesNotThrowAnyException();
        assertThatCode(() -> reader.requireAttendanceOwnedBy(2L, 3L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnAttendanceOfAnotherStudentAsNotFound() {
        Attendance program = attendanceOf(programEnrollment(7L, 3L), null);
        Attendance course = attendanceOf(null, courseEnrollment(8L, 3L));
        when(attendanceRepository.findById(1L)).thenReturn(Optional.of(program));
        when(attendanceRepository.findById(2L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> reader.requireAttendanceOwnedBy(1L, 99L)).isInstanceOf(AttendanceNotFoundException.class);
        assertThatThrownBy(() -> reader.requireAttendanceOwnedBy(2L, 99L)).isInstanceOf(AttendanceNotFoundException.class);
    }

    @Test
    void rejectsAnUnknownAttendanceAsNotFound() {
        when(attendanceRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.requireAttendanceOwnedBy(5L, 3L)).isInstanceOf(AttendanceNotFoundException.class);
    }

    static SubjectSectionEnrollment programEnrollment(Long id, Long studentId) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student(studentId));
        return enrollment;
    }

    static CourseSubjectSectionEnrollment courseEnrollment(Long id, Long studentId) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setId(id);
        enrollment.setStudent(student(studentId));
        return enrollment;
    }

    static Attendance attendanceOf(SubjectSectionEnrollment program, CourseSubjectSectionEnrollment course) {
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(program);
        attendance.setCourseSubjectSectionEnrollment(course);
        attendance.setStatus(AttendanceStatus.ABSENT);
        return attendance;
    }

    private static Student student(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }
}
