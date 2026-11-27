package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public interface SectionClosureStatusPort {

    boolean isClosed(SectionKind kind, Long sectionId);

    boolean isReopened(SectionKind kind, Long sectionId);
}
