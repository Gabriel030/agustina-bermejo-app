package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;

public record SaveSectionEvaluationConfigDraftCommand(SectionRef section, Long instructorId, UpdateSectionEvaluationConfigRequest request) {

    public SaveSectionEvaluationConfigDraftCommand(Long sectionId, Long instructorId, UpdateSectionEvaluationConfigRequest request) {
        this(SectionRef.program(sectionId), instructorId, request);
    }
}
