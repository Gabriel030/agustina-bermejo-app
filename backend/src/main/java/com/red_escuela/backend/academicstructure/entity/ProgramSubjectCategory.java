package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum ProgramSubjectCategory {
    MANDATORY,
    ADDITIONAL_MANDATORY,
    ELECTIVE
}
