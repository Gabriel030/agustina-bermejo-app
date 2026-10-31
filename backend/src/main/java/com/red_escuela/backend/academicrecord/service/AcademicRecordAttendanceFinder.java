package com.red_escuela.backend.academicrecord.service;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;
import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;
import com.red_escuela.backend.attendance.api.AttendanceRiskReader;
import com.red_escuela.backend.attendance.api.StudentAttendanceReader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicRecordAttendanceFinder {

    private final StudentAttendanceReader studentAttendanceReader;
    private final AttendanceRiskReader attendanceRiskReader;

    public AcademicRecordAttendanceFinder(
            StudentAttendanceReader studentAttendanceReader, AttendanceRiskReader attendanceRiskReader) {
        this.studentAttendanceReader = studentAttendanceReader;
        this.attendanceRiskReader = attendanceRiskReader;
    }

    public List<StudentAttendanceResponse> listProgramEnrollmentAttendance(Long enrollmentId) {
        return studentAttendanceReader.listProgramEnrollmentAttendance(enrollmentId).stream()
                .map(StudentAttendanceResponse::from)
                .toList();
    }

    public StudentAttendanceRiskResponse readProgramEnrollmentRisk(Long enrollmentId) {
        return StudentAttendanceRiskResponse.from(attendanceRiskReader.readProgramEnrollmentRisk(enrollmentId));
    }

    public void requireAttendanceOwnedBy(Long attendanceId, Long studentId) {
        studentAttendanceReader.requireAttendanceOwnedBy(attendanceId, studentId);
    }
}
