package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;

import java.util.List;

public interface ListCourseSectionEvaluationConfigsUseCase {
    List<SectionEvaluationConfigResponse> list(String statusName);
}
