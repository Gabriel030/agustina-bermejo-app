package com.red_escuela.backend.attendance.api;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AttendanceStandingFinder {

    private final AttendanceRiskReader attendanceRiskReader;

    public AttendanceStandingFinder(AttendanceRiskReader attendanceRiskReader) {
        this.attendanceRiskReader = attendanceRiskReader;
    }

    public AttendanceStandingView ofProgramSectionEnrollment(Long subjectSectionEnrollmentId) {
        return toView(attendanceRiskReader.readProgramEnrollmentRisk(subjectSectionEnrollmentId));
    }

    public AttendanceStandingView ofCourseSectionEnrollment(Long courseSectionEnrollmentId) {
        return toView(attendanceRiskReader.readCourseEnrollmentRisk(courseSectionEnrollmentId));
    }

    private AttendanceStandingView toView(AttendanceRiskView view) {
        return new AttendanceStandingView(view.calculable(), view.libre());
    }
}
