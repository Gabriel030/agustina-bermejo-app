package com.red_escuela.backend.attendance.entity;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceTest {

    private Student student(long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }

    @Test
    void aCourseAttendanceBelongsToTheStudentOfItsCourseEnrollment() {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student(7L));
        Attendance attendance = new Attendance();
        attendance.setCourseSubjectSectionEnrollment(enrollment);

        assertThat(attendance.belongsTo(student(7L))).isTrue();
        assertThat(attendance.belongsTo(student(8L))).isFalse();
    }

    @Test
    void aProgramAttendanceBelongsToTheStudentOfItsSectionEnrollment() {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student(7L));
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(enrollment);

        assertThat(attendance.belongsTo(student(7L))).isTrue();
        assertThat(attendance.belongsTo(student(8L))).isFalse();
    }
}
