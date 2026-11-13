package com.red_escuela.backend.attendance.api;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class SectionAbsenceCountsReader {

    private final AttendanceStandingReader attendanceStandingReader;

    public SectionAbsenceCountsReader(AttendanceStandingReader attendanceStandingReader) {
        this.attendanceStandingReader = attendanceStandingReader;
    }

    public Map<Long, EnrollmentAbsenceCountsView> countByProgramEnrollments(List<Long> enrollmentIds) {
        return toCounts(attendanceStandingReader.readProgramEnrollmentCounts(enrollmentIds));
    }

    public Map<Long, EnrollmentAbsenceCountsView> countByCourseEnrollments(List<Long> enrollmentIds) {
        return toCounts(attendanceStandingReader.readCourseEnrollmentCounts(enrollmentIds));
    }

    private Map<Long, EnrollmentAbsenceCountsView> toCounts(Map<Long, AttendanceStanding> standings) {
        Map<Long, EnrollmentAbsenceCountsView> byEnrollment = new HashMap<>();
        standings.forEach((enrollmentId, standing) -> byEnrollment.put(enrollmentId, new EnrollmentAbsenceCountsView(
                standing.classesHeld(), standing.countableAbsences(), standing.pendingJustificationAbsences())));
        return byEnrollment;
    }
}
