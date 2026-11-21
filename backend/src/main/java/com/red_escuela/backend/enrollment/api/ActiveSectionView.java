package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public record ActiveSectionView(SectionKind kind, Long sectionId, String sectionName, String subjectName, Long termId) {
}
