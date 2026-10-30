package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CourseModuleView(Long id, String name) {
}
