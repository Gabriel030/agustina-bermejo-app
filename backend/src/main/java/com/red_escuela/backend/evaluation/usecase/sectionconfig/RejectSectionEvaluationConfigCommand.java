package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.RejectSectionEvaluationConfigRequest;

public record RejectSectionEvaluationConfigCommand(SectionRef section, RejectSectionEvaluationConfigRequest request) {

    public RejectSectionEvaluationConfigCommand(Long sectionId, RejectSectionEvaluationConfigRequest request) {
        this(SectionRef.program(sectionId), request);
    }
}
