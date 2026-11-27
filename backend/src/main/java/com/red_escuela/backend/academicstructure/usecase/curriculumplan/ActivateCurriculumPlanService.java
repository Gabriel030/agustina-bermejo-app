package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ActivateCurriculumPlanService implements ActivateCurriculumPlanUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public ActivateCurriculumPlanService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public CurriculumPlanResponse activate(Long planId) {
        return curriculumPlanService.activate(planId);
    }
}
