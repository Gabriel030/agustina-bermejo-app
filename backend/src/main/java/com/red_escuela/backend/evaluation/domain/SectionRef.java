package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.academicstructure.domain.SectionKind;

public record SectionRef(SectionKind kind, Long id) {

    public static SectionRef program(Long id) {
        return new SectionRef(SectionKind.PROGRAM, id);
    }

    public static SectionRef course(Long id) {
        return new SectionRef(SectionKind.COURSE, id);
    }

    public boolean isCourse() {
        return kind == SectionKind.COURSE;
    }
}
