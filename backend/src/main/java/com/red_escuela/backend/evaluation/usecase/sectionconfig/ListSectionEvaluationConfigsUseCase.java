package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;

import java.util.List;

public interface ListSectionEvaluationConfigsUseCase {
    List<SectionEvaluationConfigResponse> list(SectionEvaluationConfigStatus status);
}
