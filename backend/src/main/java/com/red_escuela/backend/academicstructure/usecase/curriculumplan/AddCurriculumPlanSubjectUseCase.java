package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;

public interface AddCurriculumPlanSubjectUseCase {
    ProgramSubjectResponse addSubject(Long planId, CreateProgramSubjectRequest request);
}
