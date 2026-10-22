package com.red_escuela.backend.enrollment.api;

import java.util.Collection;
import java.util.Map;

public interface EnrollmentReminderTargetLookup {
    Map<Long, EnrollmentReminderTarget> findProgramEnrollmentTargets(Collection<Long> programEnrollmentIds);

    Map<Long, EnrollmentReminderTarget> findCourseEnrollmentTargets(Collection<Long> courseEnrollmentIds);
}
