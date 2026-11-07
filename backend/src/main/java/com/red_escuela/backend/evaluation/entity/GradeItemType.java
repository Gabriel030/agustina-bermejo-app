package com.red_escuela.backend.evaluation.entity;

public enum GradeItemType {
    EXAM("Examen"),
    TP("TP"),
    FINAL("Final");

    private final String displayName;

    GradeItemType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
