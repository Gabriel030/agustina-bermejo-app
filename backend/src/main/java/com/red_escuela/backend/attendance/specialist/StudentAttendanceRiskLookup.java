package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.api.AttendanceStanding;
import com.red_escuela.backend.attendance.api.AttendanceStandingReader;
import com.red_escuela.backend.attendance.policy.StudentAttendanceRiskPolicy;
import com.red_escuela.backend.enrollment.api.ActiveEnrollmentLimitView;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.identity.api.StudentAttendanceRisk;
import com.red_escuela.backend.identity.api.StudentAttendanceRiskPort;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAttendanceRiskLookup implements StudentAttendanceRiskPort {

    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final AttendanceStandingReader attendanceStandingReader;

    public StudentAttendanceRiskLookup(SectionEnrollmentFinder sectionEnrollmentFinder,
                                       AttendanceStandingReader attendanceStandingReader) {
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.attendanceStandingReader = attendanceStandingReader;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, StudentAttendanceRisk> riskByStudent(Collection<Long> studentIds) {
        Map<Long, StudentAttendanceRisk> risks = new HashMap<>();
        if (studentIds.isEmpty()) {
            return risks;
        }
        List<ActiveEnrollmentLimitView> programLimits = sectionEnrollmentFinder.findActiveProgramLimitsByStudents(studentIds);
        List<ActiveEnrollmentLimitView> courseLimits = sectionEnrollmentFinder.findActiveCourseLimitsByStudents(studentIds);
        mergeRisks(risks, programLimits, attendanceStandingReader.readProgramEnrollmentCounts(ids(programLimits)));
        mergeRisks(risks, courseLimits, attendanceStandingReader.readCourseEnrollmentCounts(ids(courseLimits)));
        return risks;
    }

    private void mergeRisks(Map<Long, StudentAttendanceRisk> risks, List<ActiveEnrollmentLimitView> limits,
                            Map<Long, AttendanceStanding> countsByEnrollment) {
        for (ActiveEnrollmentLimitView limit : limits) {
            AttendanceStanding counts = countsByEnrollment.get(limit.enrollmentId());
            if (counts == null) {
                continue;
            }
            risks.merge(limit.studentId(), StudentAttendanceRiskPolicy.classify(withLimit(counts, limit.maxAbsenceRate())),
                    StudentAttendanceRiskPolicy::worst);
        }
    }

    private AttendanceStanding withLimit(AttendanceStanding counts, Percentage max) {
        boolean exceeds = max != null && counts.absencePercent() != null && counts.absencePercent().exceeds(max);
        return new AttendanceStanding(counts.classesHeld(), counts.presentClasses(), counts.countableAbsences(),
                counts.pendingJustificationAbsences(), counts.absencePercent(), counts.presencePercent(), max, exceeds,
                counts.allowedAbsences(), counts.remainingAbsences());
    }

    private List<Long> ids(List<ActiveEnrollmentLimitView> limits) {
        return limits.stream().map(ActiveEnrollmentLimitView::enrollmentId).toList();
    }
}
