package com.red_escuela.backend.academicrecord.specialist;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.api.StudentAttendanceReader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicRecordAttendanceFinder {

    private final StudentAttendanceReader studentAttendanceReader;
    private final AttendanceStandingReader attendanceStandingReader;

    public AcademicRecordAttendanceFinder(
            StudentAttendanceReader studentAttendanceReader, AttendanceStandingReader attendanceStandingReader) {
        this.studentAttendanceReader = studentAttendanceReader;
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public List<StudentAttendanceResponse> listProgramEnrollmentAttendance(Long enrollmentId) {
        return studentAttendanceReader.listProgramEnrollmentAttendance(enrollmentId).stream()
                .map(StudentAttendanceResponse::from)
                .toList();
    }

    public StudentAttendanceRiskResponse readProgramEnrollmentRisk(Long enrollmentId) {
        return StudentAttendanceRiskResponse.from(attendanceStandingReader.readProgramEnrollmentStanding(enrollmentId));
    }

    public List<StudentAttendanceResponse> listCourseEnrollmentAttendance(Long enrollmentId) {
        return studentAttendanceReader.listCourseEnrollmentAttendance(enrollmentId).stream()
                .map(StudentAttendanceResponse::from)
                .toList();
    }

    public StudentAttendanceRiskResponse readCourseEnrollmentRisk(Long enrollmentId) {
        return StudentAttendanceRiskResponse.from(attendanceStandingReader.readCourseEnrollmentStanding(enrollmentId));
    }

    public void requireAttendanceOwnedBy(Long attendanceId, Long studentId) {
        studentAttendanceReader.requireAttendanceOwnedBy(attendanceId, studentId);
    }
}
