package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;

public record GetMySectionEvaluationConfigCommand(SectionRef section, Long instructorId) {

    public GetMySectionEvaluationConfigCommand(Long sectionId, Long instructorId) {
        this(SectionRef.program(sectionId), instructorId);
    }
}
