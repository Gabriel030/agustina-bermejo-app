package com.red_escuela.backend.simulation;

import java.time.LocalDate;

public record InvariantViolation(String rule, LocalDate detectedOn, LocalDate factDay, Long studentId, String detail) {

    public String key() {
        return rule + "|" + factDay + "|" + studentId + "|" + detail;
    }
}
