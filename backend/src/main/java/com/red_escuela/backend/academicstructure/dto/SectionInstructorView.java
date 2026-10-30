package com.red_escuela.backend.academicstructure.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SectionInstructorView(Long instructorId, Long personId, String fullName, InstructorRole role) {
}
