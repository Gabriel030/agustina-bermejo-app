package com.red_escuela.backend.identity.api;

public interface StudentEnrollmentActivityLookup {
    boolean hasActiveEnrollments(Long studentId);
}
