package com.red_escuela.backend.enrollment.entity;

public enum ExceptionType {
    LATE_EXAM("examen fuera de término"),
    PREREQUISITE_WAIVER("condonación de correlatividad");

    private final String displayName;

    ExceptionType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
