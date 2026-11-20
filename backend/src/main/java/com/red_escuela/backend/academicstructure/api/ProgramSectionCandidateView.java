package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record ProgramSectionCandidateView(Long id, String name, Long termId, Integer capacity, boolean openForEnrollment) {
}
