package com.red_escuela.backend.academicstructure.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CourseSectionView(Long id, Long courseSubjectId, Long courseId, Long termId, Integer capacity, String name) {
}
