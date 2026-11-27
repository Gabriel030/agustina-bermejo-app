package com.red_escuela.backend.academicstructure.usecase.curriculumplan;

import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;

import java.util.List;

public interface ListCurriculumPlanSubjectsUseCase {
    List<ProgramSubjectResponse> listSubjects(Long planId);
}
