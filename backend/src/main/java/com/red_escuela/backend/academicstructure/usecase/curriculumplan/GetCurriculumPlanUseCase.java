package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;

public interface GetCurriculumPlanUseCase {
    CurriculumPlanResponse get(Long planId);
}
