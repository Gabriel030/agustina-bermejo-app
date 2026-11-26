package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;

public interface GetSectionEvaluationConfigForStaffUseCase {
    SectionEvaluationConfigResponse get(Long sectionId);
}
