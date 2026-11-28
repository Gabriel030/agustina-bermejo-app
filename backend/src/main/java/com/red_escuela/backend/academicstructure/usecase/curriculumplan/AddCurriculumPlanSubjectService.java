package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;
import com.red_escuela.backend.academicstructure.service.CurriculumPlanService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class AddCurriculumPlanSubjectService implements AddCurriculumPlanSubjectUseCase {

    private final CurriculumPlanService curriculumPlanService;

    public AddCurriculumPlanSubjectService(CurriculumPlanService curriculumPlanService) {
        this.curriculumPlanService = curriculumPlanService;
    }

    @Override
    public ProgramSubjectResponse addSubject(Long planId, CreateProgramSubjectRequest request) {
        return curriculumPlanService.addSubject(planId, request);
    }
}
