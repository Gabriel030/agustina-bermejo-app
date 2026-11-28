package com.red_escuela.backend.simulation;

import java.math.BigDecimal;

public enum StudentProfile {
    GRADUATES,
    PASSES_EVERYTHING,
    FAILS_AND_RETAKES,
    BECOMES_FREE,
    RECOVERY_AND_FINAL,
    DROPS_OUT,
    REPEATS_YEAR;

    public static final int RETAKE_SUBJECT_INDEX = 0;
    public static final int FREE_SUBJECT_INDEX = 0;
    public static final int FINAL_SUBJECT_INDEX = 1;
    public static final BigDecimal RECOVERY_SCORE = BigDecimal.valueOf(5);
    public static final BigDecimal FINAL_SCORE = BigDecimal.valueOf(8);

    private static final BigDecimal PASSING = BigDecimal.valueOf(9);
    private static final BigDecimal FAILING = BigDecimal.valueOf(2);

    public BigDecimal examScore(int studyYear, int subjectIndex, int attempt, int sequenceNumber) {
        boolean firstAttempt = attempt == 1;
        return switch (this) {
            case FAILS_AND_RETAKES -> firstAttempt && studyYear == 1 && subjectIndex == RETAKE_SUBJECT_INDEX ? FAILING : PASSING;
            case REPEATS_YEAR -> firstAttempt && studyYear == 1 ? FAILING : PASSING;
            case RECOVERY_AND_FINAL -> studyYear == 1 && subjectIndex == FINAL_SUBJECT_INDEX ? (sequenceNumber == 1 ? FAILING : BigDecimal.valueOf(6)) : PASSING;
            default -> PASSING;
        };
    }

    public double absenceRate(int studyYear, int subjectIndex, int attempt) {
        if (this == BECOMES_FREE && attempt == 1 && studyYear == 1 && subjectIndex == FREE_SUBJECT_INDEX) {
            return 0.9;
        }
        return 0.02;
    }

    public boolean needsRecovery(int studyYear, int subjectIndex, int attempt) {
        return this == RECOVERY_AND_FINAL && attempt == 1 && studyYear == 1 && subjectIndex == FINAL_SUBJECT_INDEX;
    }
}
