package com.red_escuela.backend.enrollment.policy;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.exception.EnrollmentNotActiveException;
import com.red_escuela.backend.enrollment.exception.EquivalenceAlreadyVoidedException;
import com.red_escuela.backend.enrollment.exception.EquivalenceReasonInvalidException;
import com.red_escuela.backend.enrollment.exception.FutureDateException;
import com.red_escuela.backend.enrollment.exception.InvalidScoreException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.InvalidGradeException;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class SubjectEquivalencePolicy {

    public static final String ACTIVE_UNIQUE_INDEX = "uk_subject_equivalence_active";
    public static final int MIN_REASON_LENGTH = 10;
    public static final int MAX_REASON_LENGTH = 500;

    private SubjectEquivalencePolicy() {
    }

    public static void requireActiveEnrollment(Long programEnrollmentId, ProgramEnrollmentStatus status) {
        if (status != ProgramEnrollmentStatus.ENROLLED) {
            throw new EnrollmentNotActiveException(programEnrollmentId);
        }
    }

    public static Grade parseOriginScore(BigDecimal rawScore) {
        if (rawScore == null) {
            return null;
        }
        try {
            return Grade.of(rawScore);
        } catch (InvalidGradeException exception) {
            throw new InvalidScoreException("debe estar entre 1 y 10 con hasta 2 decimales");
        }
    }

    public static void requireNotFuture(LocalDate recognizedOn, LocalDate today) {
        if (recognizedOn.isAfter(today)) {
            throw new FutureDateException(recognizedOn);
        }
    }

    public static String normalizeReason(String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.length() < MIN_REASON_LENGTH || reason.length() > MAX_REASON_LENGTH) {
            throw new EquivalenceReasonInvalidException(MIN_REASON_LENGTH, MAX_REASON_LENGTH);
        }
        return reason;
    }

    public static boolean isActiveUniquenessViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(ACTIVE_UNIQUE_INDEX)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    public static void requireActive(SubjectEquivalence equivalence) {
        if (equivalence.getStatus() == SubjectEquivalenceStatus.VOIDED) {
            throw new EquivalenceAlreadyVoidedException(equivalence.getId());
        }
    }
}
