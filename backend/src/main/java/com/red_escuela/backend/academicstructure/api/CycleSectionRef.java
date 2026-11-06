package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public record CycleSectionRef(SectionKind kind, Long id, String name, Integer capacity) {
}
