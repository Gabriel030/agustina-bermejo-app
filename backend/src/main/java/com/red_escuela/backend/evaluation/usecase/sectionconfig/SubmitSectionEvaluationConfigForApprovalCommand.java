package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;

public record SubmitSectionEvaluationConfigForApprovalCommand(SectionRef section, Long instructorId) {

    public SubmitSectionEvaluationConfigForApprovalCommand(Long sectionId, Long instructorId) {
        this(SectionRef.program(sectionId), instructorId);
    }
}
