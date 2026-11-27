package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExpireCurriculumPlanService implements ExpireCurriculumPlanUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public ExpireCurriculumPlanService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public CurriculumPlanResponse expire(Long planId, ExpireCurriculumPlanRequest request) {
        return curriculumPlanService.expire(planId, request);
    }
}
