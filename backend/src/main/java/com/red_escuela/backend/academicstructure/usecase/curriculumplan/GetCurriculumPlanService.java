package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCurriculumPlanService implements GetCurriculumPlanUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public GetCurriculumPlanService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public CurriculumPlanResponse get(Long planId) {
        return curriculumPlanService.get(planId);
    }
}
