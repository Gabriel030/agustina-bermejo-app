package com.red_escuela.backend.billing.planning.domain;

public enum EnrollmentFeeObligationStatus {
    NOT_ISSUED, PENDING, IN_REVIEW, PAID, EXEMPTED, CANCELLED;

    public boolean isSatisfied() {
        return this == PAID || this == EXEMPTED;
    }
}
