package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public record PublishedSectionRef(SectionKind sectionKind, Long sectionId) {
}
