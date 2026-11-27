package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateCurriculumPlanService implements UpdateCurriculumPlanUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public UpdateCurriculumPlanService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public CurriculumPlanResponse update(Long planId, UpdateCurriculumPlanRequest request) {
        return curriculumPlanService.update(planId, request);
    }
}
