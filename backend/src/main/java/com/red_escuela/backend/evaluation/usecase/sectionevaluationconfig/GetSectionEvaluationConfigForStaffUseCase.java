package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

public interface GetSectionEvaluationConfigForStaffUseCase {
    SectionEvaluationConfig get(Long sectionId);
}
