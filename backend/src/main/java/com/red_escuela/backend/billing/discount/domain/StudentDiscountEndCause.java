package com.red_escuela.backend.billing.discount.domain;

import java.util.Optional;

public enum StudentDiscountEndCause {
    INSTITUTION_DECISION("Decisión de la institución"),
    ENROLLMENT_ENDED("Finalizó la inscripción");

    private final String studentFacingText;

    StudentDiscountEndCause(String studentFacingText) {
        this.studentFacingText = studentFacingText;
    }

    public String studentFacingText() {
        return studentFacingText;
    }

    public static Optional<StudentDiscountEndCause> of(StudentDiscountStatus status) {
        return switch (status) {
            case REVOKED -> Optional.of(INSTITUTION_DECISION);
            case ENDED -> Optional.of(ENROLLMENT_ENDED);
            case ACTIVE, EXHAUSTED -> Optional.empty();
        };
    }
}
