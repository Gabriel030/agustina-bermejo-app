package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import org.springframework.stereotype.Service;

@Service
public class AttendanceRiskCalculator {

    private final AttendanceStandingReader attendanceStandingReader;

    public AttendanceRiskCalculator(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public AttendanceRiskResponse calculateForProgramEnrollment(Long enrollmentId) {
        return toResponse(attendanceStandingReader.readProgramEnrollmentStanding(enrollmentId));
    }

    public AttendanceRiskResponse calculateForCourseEnrollment(Long enrollmentId) {
        return toResponse(attendanceStandingReader.readCourseEnrollmentStanding(enrollmentId));
    }

    private AttendanceRiskResponse toResponse(AttendanceStanding standing) {
        return new AttendanceRiskResponse(standing.calculable(), standing.exceedsAbsenceLimit(),
                standing.calculable() ? standing.absencePercent() : null, standing.maxAbsencePercent());
    }
}
