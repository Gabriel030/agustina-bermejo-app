package com.red_escuela.backend.academicstructure.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum InstructorRole {
    LEAD,
    ASSISTANT,
    PRACTICAL,
    LAB,
    GUEST
}
