package com.red_escuela.backend.attendance.policy;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceSameDayCorrectionPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 5, 10);

    private final AttendanceSameDayCorrectionPolicy policy = new AttendanceSameDayCorrectionPolicy();

    @Test
    void allowsCorrectionOfAnAttendanceRecordedTodayByTheSameInstructor() {
        Instructor instructor = instructor(7L);

        assertThat(policy.allowsCorrection(attendance(1L, AttendanceStatus.PRESENT, TODAY, instructor), instructor, TODAY)).isTrue();
    }

    @Test
    void deniesCorrectionOfAnAttendanceFromAnotherDay() {
        Instructor instructor = instructor(7L);

        assertThat(policy.allowsCorrection(attendance(1L, AttendanceStatus.PRESENT, TODAY.minusDays(1), instructor), instructor, TODAY)).isFalse();
    }

    @Test
    void deniesCorrectionByAnotherInstructor() {
        Attendance attendance = attendance(1L, AttendanceStatus.ABSENT, TODAY, instructor(7L));

        assertThat(policy.allowsCorrection(attendance, instructor(8L), TODAY)).isFalse();
    }

    @Test
    void deniesCorrectionOfAnAttendanceNotYetPersisted() {
        Instructor instructor = instructor(7L);

        assertThat(policy.allowsCorrection(attendance(null, AttendanceStatus.PRESENT, TODAY, instructor), instructor, TODAY)).isFalse();
    }

    @Test
    void deniesCorrectionOfAStatusAnInstructorCannotRecord() {
        Instructor instructor = instructor(7L);

        assertThat(policy.allowsCorrection(attendance(1L, AttendanceStatus.JUSTIFIED, TODAY, instructor), instructor, TODAY)).isFalse();
    }

    private static Instructor instructor(Long id) {
        Instructor instructor = new Instructor();
        instructor.setId(id);
        return instructor;
    }

    private static Attendance attendance(Long id, AttendanceStatus status, LocalDate classDate, Instructor recordedBy) {
        Attendance attendance = new Attendance();
        attendance.setId(id);
        attendance.setStatus(status);
        attendance.setClassDate(classDate);
        attendance.setRecordedBy(recordedBy);
        return attendance;
    }
}
