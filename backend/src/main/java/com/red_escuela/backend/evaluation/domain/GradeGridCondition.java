package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;

public enum GradeGridCondition {
    IN_PROGRESS,
    REGULAR,
    FREE,
    APPROVED,
    FAILED;

    public static GradeGridCondition of(EnrollmentStatus status, boolean freeByAbsences) {
        return switch (status) {
            case REGULAR -> REGULAR;
            case APPROVED -> APPROVED;
            case FAILED -> FAILED;
            case ENROLLED, IN_PROGRESS -> freeByAbsences ? FREE : IN_PROGRESS;
            case WITHDRAWN, WAITLISTED, WAITLIST_EXPIRED ->
                    throw new IllegalArgumentException("Estado sin condicion en la grilla: " + status);
        };
    }
}
