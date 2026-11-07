package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

public interface SaveSectionEvaluationConfigDraftUseCase {
    SectionEvaluationConfig save(SaveSectionEvaluationConfigDraftCommand command);
}
