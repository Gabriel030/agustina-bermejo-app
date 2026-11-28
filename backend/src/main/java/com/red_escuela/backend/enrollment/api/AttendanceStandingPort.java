package com.red_escuela.backend.enrollment.api;

import java.util.Collection;
import java.util.Map;

public interface AttendanceStandingPort {

    Map<Long, SubjectAttendanceView> readProgramSectionAttendance(Collection<Long> subjectSectionEnrollmentIds);
}
