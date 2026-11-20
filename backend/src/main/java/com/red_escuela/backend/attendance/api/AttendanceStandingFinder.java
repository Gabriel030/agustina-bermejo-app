package com.red_escuela.backend.attendance.api;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AttendanceStandingFinder {

    private final AttendanceStandingReader attendanceStandingReader;

    public AttendanceStandingFinder(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public AttendanceStandingView ofProgramSectionEnrollment(Long subjectSectionEnrollmentId) {
        return toView(attendanceStandingReader.readProgramEnrollmentStanding(subjectSectionEnrollmentId));
    }

    public AttendanceStandingView ofCourseSectionEnrollment(Long courseSectionEnrollmentId) {
        return toView(attendanceStandingReader.readCourseEnrollmentStanding(courseSectionEnrollmentId));
    }

    private AttendanceStandingView toView(AttendanceStanding standing) {
        return new AttendanceStandingView(standing.calculable(), standing.exceedsAbsenceLimit());
    }
}
