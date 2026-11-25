package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.util.List;

public interface ListMySectionEvaluationConfigsUseCase {
    List<SectionEvaluationConfig> list(Long instructorId);
}
