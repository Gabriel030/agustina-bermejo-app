package com.red_escuela.backend.enrollment.api;

import java.util.Collection;
import java.util.Map;

public interface EnrollmentNameLookup {

    Map<Long, String> findProgramNamesByProgramEnrollmentId(Collection<Long> programEnrollmentIds);

    Map<Long, String> findCourseNamesByCourseEnrollmentId(Collection<Long> courseEnrollmentIds);
}
