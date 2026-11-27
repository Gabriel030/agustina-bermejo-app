package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;

import java.util.List;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCurriculumPlansService implements ListCurriculumPlansUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public ListCurriculumPlansService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public List<CurriculumPlanResponse> listByProgram(Long programId) {
        return curriculumPlanService.listByProgram(programId);
    }
}
