package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class EnrollmentReminderTargetFinder {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EnrollmentBillingStandingPort standingPort;

    public EnrollmentReminderTargetFinder(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                                    EnrollmentBillingStandingPort standingPort) {
        this.standingPort = standingPort;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, EnrollmentReminderTarget> findProgramEnrollmentTargets(Collection<Long> programEnrollmentIds) {
        Map<Long, EnrollmentReminderTarget> targets = new HashMap<>();
        if (programEnrollmentIds.isEmpty()) {
            return targets;
        }
        Set<Long> suspendedIds = standingPort.findSuspendedProgramEnrollmentIds(programEnrollmentIds);
        programEnrollmentRepository.findReminderTargetRows(programEnrollmentIds).forEach(row -> targets.put(row.getEnrollmentId(),
                new EnrollmentReminderTarget(row.getEnrollmentId(), row.getTargetId(), null, suspendedIds.contains(row.getEnrollmentId()))));
        return targets;
    }

    @Transactional(readOnly = true)
    public Map<Long, EnrollmentReminderTarget> findCourseEnrollmentTargets(Collection<Long> courseEnrollmentIds) {
        Map<Long, EnrollmentReminderTarget> targets = new HashMap<>();
        if (courseEnrollmentIds.isEmpty()) {
            return targets;
        }
        Set<Long> suspendedIds = standingPort.findSuspendedCourseEnrollmentIds(courseEnrollmentIds);
        courseEnrollmentRepository.findReminderTargetRows(courseEnrollmentIds).forEach(row -> targets.put(row.getEnrollmentId(),
                new EnrollmentReminderTarget(row.getEnrollmentId(), null, row.getTargetId(), suspendedIds.contains(row.getEnrollmentId()))));
        return targets;
    }
}
