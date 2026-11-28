package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

import java.time.LocalDate;

public record EndedSectionRef(SectionKind kind, Long sectionId, LocalDate termEnd) {
}
