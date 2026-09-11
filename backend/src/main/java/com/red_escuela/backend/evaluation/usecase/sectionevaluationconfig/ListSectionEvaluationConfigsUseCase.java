package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;

import java.util.List;

public interface ListSectionEvaluationConfigsUseCase {
    List<SectionEvaluationConfig> list(SectionEvaluationConfigStatus status);
}
