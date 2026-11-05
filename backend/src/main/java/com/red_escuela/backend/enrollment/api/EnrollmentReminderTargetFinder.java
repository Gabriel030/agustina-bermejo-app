package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class EnrollmentReminderTargetFinder {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EnrollmentPaymentStandingQuery standingQuery;

    public EnrollmentReminderTargetFinder(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                                    EnrollmentPaymentStandingQuery standingQuery) {
        this.standingQuery = standingQuery;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, EnrollmentReminderTarget> findProgramEnrollmentTargets(Collection<Long> programEnrollmentIds) {
        Map<Long, EnrollmentReminderTarget> targets = new HashMap<>();
        if (programEnrollmentIds.isEmpty()) {
            return targets;
        }
        Map<Long, EnrollmentPaymentStatus> statuses = standingQuery.findStatusesOfProgramEnrollments(programEnrollmentIds);
        programEnrollmentRepository.findReminderTargetRows(programEnrollmentIds).forEach(row -> targets.put(row.getEnrollmentId(),
                new EnrollmentReminderTarget(row.getEnrollmentId(), row.getTargetId(), null, statuses.get(row.getEnrollmentId()) == EnrollmentPaymentStatus.SUSPENDED)));
        return targets;
    }

    @Transactional(readOnly = true)
    public Map<Long, EnrollmentReminderTarget> findCourseEnrollmentTargets(Collection<Long> courseEnrollmentIds) {
        Map<Long, EnrollmentReminderTarget> targets = new HashMap<>();
        if (courseEnrollmentIds.isEmpty()) {
            return targets;
        }
        Map<Long, EnrollmentPaymentStatus> statuses = standingQuery.findStatusesOfCourseEnrollments(courseEnrollmentIds);
        courseEnrollmentRepository.findReminderTargetRows(courseEnrollmentIds).forEach(row -> targets.put(row.getEnrollmentId(),
                new EnrollmentReminderTarget(row.getEnrollmentId(), null, row.getTargetId(), statuses.get(row.getEnrollmentId()) == EnrollmentPaymentStatus.SUSPENDED)));
        return targets;
    }
}
