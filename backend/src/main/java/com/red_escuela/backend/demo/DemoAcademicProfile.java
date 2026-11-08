package com.red_escuela.backend.demo;

import java.math.BigDecimal;
import java.util.List;

public enum DemoAcademicProfile {
    EXCELLENT(List.of(8, 9), 0),
    REGULAR(List.of(5, 6), 1),
    FAILING(List.of(2, 3), 3),
    PENDING(List.of(7), 2);

    private final List<Integer> examScores;
    private final int absences;

    DemoAcademicProfile(List<Integer> examScores, int absences) {
        this.examScores = examScores;
        this.absences = absences;
    }

    public static DemoAcademicProfile of(int rosterPosition, int sectionPosition) {
        return values()[(rosterPosition + sectionPosition) % values().length];
    }

    public List<BigDecimal> examScores() {
        return examScores.stream().map(BigDecimal::valueOf).toList();
    }

    public int absences() {
        return absences;
    }
}
