package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateCurriculumPlanService implements CreateCurriculumPlanUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public CreateCurriculumPlanService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public CurriculumPlanResponse createDraftVersion(Long programId, CreateCurriculumPlanRequest request) {
        return curriculumPlanService.createDraftVersion(programId, request);
    }
}
