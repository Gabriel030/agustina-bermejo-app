package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;

import java.util.List;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCurriculumPlanSubjectsService implements ListCurriculumPlanSubjectsUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public ListCurriculumPlanSubjectsService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public List<ProgramSubjectResponse> listSubjects(Long planId) {
        return curriculumPlanService.listSubjects(planId);
    }
}
