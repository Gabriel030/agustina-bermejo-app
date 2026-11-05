package com.red_escuela.backend.enrollment.api;

import java.util.Objects;

public record EnrollmentReminderTarget(Long enrollmentId, Long programId, Long courseId, boolean suspended) {

    public EnrollmentReminderTarget {
        Objects.requireNonNull(enrollmentId, "enrollmentId");
    }
}
