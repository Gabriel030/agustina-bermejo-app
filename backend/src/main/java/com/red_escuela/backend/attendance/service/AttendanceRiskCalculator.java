package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.api.AttendanceRiskReader;
import com.red_escuela.backend.attendance.api.AttendanceRiskView;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;

@Service
public class AttendanceRiskCalculator {

    private final AttendanceRiskReader attendanceRiskReader;

    public AttendanceRiskCalculator(AttendanceRiskReader attendanceRiskReader) {
        this.attendanceRiskReader = attendanceRiskReader;
    }

    public AttendanceRiskResponse calculateForProgramEnrollment(Long enrollmentId) {
        return toResponse(attendanceRiskReader.readProgramEnrollmentRisk(enrollmentId));
    }

    public AttendanceRiskResponse calculateForCourseEnrollment(Long enrollmentId) {
        return toResponse(attendanceRiskReader.readCourseEnrollmentRisk(enrollmentId));
    }

    public AttendanceRiskResponse evaluate(Percentage maxAbsenceRate, long total, long absences) {
        return toResponse(attendanceRiskReader.evaluateRisk(maxAbsenceRate, total, absences));
    }

    private AttendanceRiskResponse toResponse(AttendanceRiskView view) {
        return new AttendanceRiskResponse(view.calculable(), view.libre(), view.absencePercent(), view.maxAbsencePercent());
    }
}
