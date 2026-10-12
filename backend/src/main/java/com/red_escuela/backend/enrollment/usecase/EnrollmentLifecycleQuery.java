package com.red_escuela.backend.enrollment.usecase;

import java.time.LocalDate;
import java.util.Optional;

public interface EnrollmentLifecycleQuery {

    Optional<LocalDate> findProgramEnrollmentEndDate(Long programEnrollmentId);

    Optional<LocalDate> findCourseEnrollmentEndDate(Long courseEnrollmentId);
}
