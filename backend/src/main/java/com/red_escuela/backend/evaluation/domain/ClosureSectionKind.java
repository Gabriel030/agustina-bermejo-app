package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public enum ClosureSectionKind {
    PROGRAM(SectionKind.PROGRAM),
    COURSE(SectionKind.COURSE);

    private final SectionKind sectionKind;

    ClosureSectionKind(SectionKind sectionKind) {
        this.sectionKind = sectionKind;
    }

    public SectionKind toSectionKind() {
        return sectionKind;
    }
}
