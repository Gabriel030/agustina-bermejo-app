package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import org.springframework.stereotype.Service;

@Service
public class AbsenceFreeStatusChecker {

    private final AttendanceStandingReader attendanceStandingReader;

    public AbsenceFreeStatusChecker(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public boolean isProgramEnrollmentFreeByAbsences(Long enrollmentId) {
        return attendanceStandingReader.readProgramEnrollmentStanding(enrollmentId).exceedsAbsenceLimit();
    }

    public boolean isCourseEnrollmentFreeByAbsences(Long enrollmentId) {
        return attendanceStandingReader.readCourseEnrollmentStanding(enrollmentId).exceedsAbsenceLimit();
    }
}
