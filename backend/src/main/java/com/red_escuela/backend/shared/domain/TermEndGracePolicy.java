package com.red_escuela.backend.shared.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class TermEndGracePolicy {

    public static final int GRACE_DAYS = 14;

    private TermEndGracePolicy() {
    }

    public static long daysAfter(LocalDate endDate, LocalDate today) {
        return ChronoUnit.DAYS.between(endDate, today);
    }

    public static boolean isGraceOver(LocalDate endDate, LocalDate today) {
        return daysAfter(endDate, today) > GRACE_DAYS;
    }
}
