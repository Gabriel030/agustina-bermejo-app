package com.red_escuela.backend.enrollment.entity;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum EnrollmentStatus {
    ENROLLED,
    IN_PROGRESS,
    REGULAR,
    APPROVED,
    FAILED,
    WITHDRAWN,
    WAITLISTED,

    WAITLIST_EXPIRED
}
