package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.enrollment.api.SectionAbsenceLimitPort;
import org.springframework.stereotype.Component;

@Component
public class ProgramSectionAbsenceLimitChecker implements SectionAbsenceLimitPort {

    private final AttendanceStandingReader attendanceStandingReader;

    public ProgramSectionAbsenceLimitChecker(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    @Override
    public boolean exceedsAbsenceLimit(Long programSectionEnrollmentId) {
        return attendanceStandingReader.readProgramEnrollmentStanding(programSectionEnrollmentId).exceedsAbsenceLimit();
    }

    @Override
    public boolean exceedsCourseAbsenceLimit(Long courseSectionEnrollmentId) {
        return attendanceStandingReader.readCourseEnrollmentStanding(courseSectionEnrollmentId).exceedsAbsenceLimit();
    }
}
