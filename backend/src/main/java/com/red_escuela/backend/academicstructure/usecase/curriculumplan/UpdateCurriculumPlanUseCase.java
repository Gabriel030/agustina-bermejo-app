package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;

public interface UpdateCurriculumPlanUseCase {
    CurriculumPlanResponse update(Long planId, UpdateCurriculumPlanRequest request);
}
