package com.red_escuela.backend.billing.discount.domain;

import java.util.Objects;

public record EnrollmentReference(EnrollmentKind kind, Long id) {

    public EnrollmentReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static EnrollmentReference ofProgram(Long programEnrollmentId) {
        return new EnrollmentReference(EnrollmentKind.PROGRAM, programEnrollmentId);
    }

    public static EnrollmentReference ofCourse(Long courseEnrollmentId) {
        return new EnrollmentReference(EnrollmentKind.COURSE, courseEnrollmentId);
    }
}
