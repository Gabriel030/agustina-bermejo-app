package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum ProgramEnrollmentStatus {
    ENROLLED,
    PAUSED,
    COMPLETED,
    WITHDRAWN
}
