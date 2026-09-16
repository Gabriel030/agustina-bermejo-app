package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

public interface RejectSectionEvaluationConfigUseCase {
    SectionEvaluationConfig reject(RejectSectionEvaluationConfigCommand command);
}
