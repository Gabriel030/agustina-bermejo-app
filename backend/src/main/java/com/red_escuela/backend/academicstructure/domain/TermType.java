package com.red_escuela.backend.academicstructure.domain;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum TermType {
    ANNUAL("Anual"),
    FIRST_SEMESTER("Primer cuatrimestre"),
    SECOND_SEMESTER("Segundo cuatrimestre"),
    INTENSIVE("Intensivo de verano");

    private final String label;

    TermType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
