package com.red_escuela.backend.identity.domain;

import java.time.LocalDate;

public final class LegalAge {
    public static final int ADULT_YEARS = 18;

    private LegalAge() {
    }

    public static boolean isMinor(LocalDate birthDate, LocalDate today) {
        return birthDate.plusYears(ADULT_YEARS).isAfter(today);
    }
}
