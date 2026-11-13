package com.red_escuela.backend.enrollment.api;

public interface SectionAbsenceLimitPort {

    boolean exceedsAbsenceLimit(Long programSectionEnrollmentId);
}
