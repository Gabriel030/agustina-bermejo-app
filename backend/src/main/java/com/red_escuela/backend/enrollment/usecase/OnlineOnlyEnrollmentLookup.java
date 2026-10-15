package com.red_escuela.backend.enrollment.usecase;

import java.util.Collection;
import java.util.Set;

public interface OnlineOnlyEnrollmentLookup {

    Set<Long> findOnlineOnlyProgramEnrollmentIds(Collection<Long> programEnrollmentIds);

    Set<Long> findOnlineOnlyCourseEnrollmentIds(Collection<Long> courseEnrollmentIds);
}
