package com.red_escuela.backend.enrollment.api;

public interface ActiveEnrollmentCounter {
    long countActiveProgramEnrollments(Long programId);

    long countActiveCourseEnrollments(Long courseId);
}
