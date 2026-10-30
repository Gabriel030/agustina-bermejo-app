package com.red_escuela.backend.evaluation.api;

import java.util.Map;

public record EnrollmentGradeScores(
        Map<Integer, Double> examScores,
        Map<Integer, Double> tpScores,
        Map<Integer, Double> finalScores) {

    public static EnrollmentGradeScores empty() {
        return new EnrollmentGradeScores(Map.of(), Map.of(), Map.of());
    }
}
