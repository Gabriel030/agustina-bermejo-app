package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;

public interface CreateCurriculumPlanUseCase {
    CurriculumPlanResponse createDraftVersion(Long programId, CreateCurriculumPlanRequest request);
}
