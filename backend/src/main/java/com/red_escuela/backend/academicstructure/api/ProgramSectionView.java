package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ProgramSectionView(Long id, Long subjectId, Long termId, Integer capacity, String name) {
}
