package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;

public record ApproveSectionEvaluationConfigCommand(SectionRef section) {

    public ApproveSectionEvaluationConfigCommand(Long sectionId) {
        this(SectionRef.program(sectionId));
    }
}
