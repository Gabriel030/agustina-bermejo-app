package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.attendance.api.AttendanceRiskReader;
import org.springframework.stereotype.Service;

@Service
public class AbsenceFreeStatusChecker {

    private final AttendanceRiskReader attendanceRiskReader;

    public AbsenceFreeStatusChecker(AttendanceRiskReader attendanceRiskReader) {
        this.attendanceRiskReader = attendanceRiskReader;
    }

    public boolean isProgramEnrollmentFreeByAbsences(Long enrollmentId) {
        return attendanceRiskReader.readProgramEnrollmentRisk(enrollmentId).libre();
    }

    public boolean isCourseEnrollmentFreeByAbsences(Long enrollmentId) {
        return attendanceRiskReader.readCourseEnrollmentRisk(enrollmentId).libre();
    }
}
