package com.red_escuela.backend.config.demo;

public enum DemoOutcome {
    PROMOTION,
    FINAL_PASSED,
    FINAL_FAILED,
    REGULAR_PENDING,
    FAILED_COURSEWORK,
    ON_TRACK,
    STRUGGLING,
    AT_RISK;

    public boolean approves() {
        return this == PROMOTION || this == FINAL_PASSED;
    }

    public boolean fails() {
        return this == FINAL_FAILED || this == FAILED_COURSEWORK;
    }

    public boolean running() {
        return this == ON_TRACK || this == STRUGGLING || this == AT_RISK;
    }
}
