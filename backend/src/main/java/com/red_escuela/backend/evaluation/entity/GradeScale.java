package com.red_escuela.backend.evaluation.entity;

public final class GradeScale {

    public static final double MIN = 0.0;
    public static final double MAX = 10.0;

    private GradeScale() {
    }

    public static boolean contains(double grade) {
        return grade >= MIN && grade <= MAX;
    }
}
