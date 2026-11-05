package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.entity.Term;

import java.time.LocalDateTime;

public final class TermFixtures {

    public static final LocalDateTime OPEN_WINDOW_START = LocalDateTime.of(2000, 1, 1, 0, 0);
    public static final LocalDateTime OPEN_WINDOW_END = LocalDateTime.of(2999, 12, 31, 23, 59);

    private TermFixtures() {
    }

    public static Term newTerm() {
        Term term = new Term();
        term.setStartDate(OPEN_WINDOW_START);
        term.setEndDate(OPEN_WINDOW_END);
        term.defineEnrollmentWindow(OPEN_WINDOW_START, OPEN_WINDOW_END);
        return term;
    }
}
