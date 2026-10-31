package com.red_escuela.backend.identity.api;

public interface StudentEnrollmentActivityPort {
    boolean hasActiveEnrollments(Long studentId);
}
