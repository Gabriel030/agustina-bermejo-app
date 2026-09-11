package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

public interface GetMySectionEvaluationConfigUseCase {
    SectionEvaluationConfig get(GetMySectionEvaluationConfigCommand command);
}
