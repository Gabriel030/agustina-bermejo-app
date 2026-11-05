package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SectionInstructorView(Long instructorId, Long personId, String fullName, InstructorRole role) {
}
