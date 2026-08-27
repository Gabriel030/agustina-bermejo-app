package com.red_escuela.backend.entity;

// null en Term.enrollmentOverride = sin override, se sigue validando por fecha (default).
public enum TermEnrollmentOverride {
    OPEN,
    CLOSED
}
