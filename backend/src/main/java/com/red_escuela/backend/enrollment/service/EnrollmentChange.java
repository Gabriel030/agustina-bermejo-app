package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.exception.EnrollmentChangeReasonInvalidException;
import com.red_escuela.backend.enrollment.exception.EnrollmentChangeReasonRequiredException;

import java.util.Objects;

public record EnrollmentChange(String reason, Long actorPersonId, Initiator initiator) {

    public static final int MIN_REASON_LENGTH = 10;
    public static final int MAX_REASON_LENGTH = 500;

    public enum Initiator { STAFF, STUDENT }

    public EnrollmentChange {
        Objects.requireNonNull(initiator, "initiator");
    }

    public static EnrollmentChange byStaff(String rawReason, Long staffPersonId) {
        return new EnrollmentChange(normalizeRequired(rawReason), Objects.requireNonNull(staffPersonId, "staffPersonId"), Initiator.STAFF);
    }

    public static EnrollmentChange byStudent() {
        return new EnrollmentChange(null, null, Initiator.STUDENT);
    }

    public static String normalizeRequired(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new EnrollmentChangeReasonRequiredException();
        }
        String normalized = rawReason.strip().replaceAll("\\s+", " ");
        if (normalized.length() < MIN_REASON_LENGTH || normalized.length() > MAX_REASON_LENGTH) {
            throw new EnrollmentChangeReasonInvalidException(MIN_REASON_LENGTH, MAX_REASON_LENGTH);
        }
        return normalized;
    }

    public boolean madeByStaff() {
        return initiator == Initiator.STAFF;
    }
}
