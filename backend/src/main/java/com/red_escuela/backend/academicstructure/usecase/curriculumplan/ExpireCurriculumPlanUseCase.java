package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;

public interface ExpireCurriculumPlanUseCase {
    CurriculumPlanResponse expire(Long planId, ExpireCurriculumPlanRequest request);
}
