package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CourseRegistrationView(Long courseId, Long termId, boolean windowInverted, boolean open) {
}
