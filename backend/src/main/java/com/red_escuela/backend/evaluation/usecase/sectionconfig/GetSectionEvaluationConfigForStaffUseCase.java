package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;

public interface GetSectionEvaluationConfigForStaffUseCase {
    SectionEvaluationConfigResponse get(SectionRef section);
}
