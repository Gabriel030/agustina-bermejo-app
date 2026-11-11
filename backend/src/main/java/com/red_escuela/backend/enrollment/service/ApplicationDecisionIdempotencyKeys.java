package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyConflictException;
import com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyInvalidException;
import com.red_escuela.backend.enrollment.exception.InvalidProgramEnrollmentApplicationStateException;

public final class ApplicationDecisionIdempotencyKeys {

    private static final int MAX_LENGTH = 100;

    private ApplicationDecisionIdempotencyKeys() {
    }

    public static boolean isReplayOfApproval(ProgramEnrollmentApplication application, String key) {
        if (application.wasDecidedWithKey(key) && application.getStatus() == ProgramEnrollmentApplicationStatus.EXPIRED) {
            throw new InvalidProgramEnrollmentApplicationStateException(application.getId(), application.getStatus());
        }
        return isReplayOf(application, key, ProgramEnrollmentApplicationStatus.APPROVED);
    }

    public static boolean isReplayOfRejection(ProgramEnrollmentApplication application, String key) {
        return isReplayOf(application, key, ProgramEnrollmentApplicationStatus.REJECTED);
    }

    private static boolean isReplayOf(ProgramEnrollmentApplication application, String key, ProgramEnrollmentApplicationStatus decision) {
        if (!application.wasDecidedWithKey(key)) {
            return false;
        }
        if (application.getStatus() == decision) {
            return true;
        }
        throw new ApplicationDecisionIdempotencyKeyConflictException(application.getId());
    }

    public static void remember(ProgramEnrollmentApplication application, String key) {
        application.recordDecisionKey(key);
    }

    public static String normalize(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (key.length() > MAX_LENGTH) {
            throw new ApplicationDecisionIdempotencyKeyInvalidException();
        }
        return key;
    }
}
